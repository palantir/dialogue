/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.dialogue.hc4;

import com.codahale.metrics.Meter;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.client.config.CipherSuites;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.dialogue.blocking.BlockingChannelAdapter;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.core.DialogueInternalWeakReducingGauge;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.IdleConnectionEvictor;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.pool.PoolStats;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApacheHttpClientChannels {
    private static final Logger log = LoggerFactory.getLogger(ApacheHttpClientChannels.class);

    private ApacheHttpClientChannels() {}

    public static Channel create(ClientConfiguration conf) {
        String channelName = "apache-channel";
        CloseableClient client = createCloseableHttpClient(conf, channelName);
        return DialogueChannel.builder()
                .channelName(channelName)
                .clientConfiguration(conf)
                .channelFactory(uri -> createSingleUri(uri, client))
                .build();
    }

    public static Channel createSingleUri(String uri, CloseableClient client) {
        BlockingChannel blockingChannel = new ApacheHttpClientBlockingChannel(client, url(uri), client.leakDetector);
        return client.executor == null
                ? BlockingChannelAdapter.of(blockingChannel)
                : BlockingChannelAdapter.of(blockingChannel, client.executor);
    }

    /**
     * Prefer {@link #clientBuilder()}.
     *
     * @deprecated Use the builder
     */
    @Deprecated
    public static CloseableClient createCloseableHttpClient(ClientConfiguration conf) {
        return createCloseableHttpClient(conf, "apache-channel");
    }

    public static CloseableClient createCloseableHttpClient(ClientConfiguration conf, String clientName) {
        return clientBuilder().clientConfiguration(conf).clientName(clientName).build();
    }

    private static void setupConnectionPoolMetrics(
            TaggedMetricRegistry taggedMetrics,
            String clientName,
            PoolingHttpClientConnectionManager connectionManager) {

        DialogueInternalWeakReducingGauge.getOrCreate(
                taggedMetrics,
                DialogueClientPoolMetrics.of(taggedMetrics)
                        .size()
                        .clientName(clientName)
                        .state("idle")
                        .buildMetricName(),
                pool -> pool.getTotalStats().getAvailable(),
                LongStream::sum,
                connectionManager);
        DialogueInternalWeakReducingGauge.getOrCreate(
                taggedMetrics,
                DialogueClientPoolMetrics.of(taggedMetrics)
                        .size()
                        .clientName(clientName)
                        .state("leased")
                        .buildMetricName(),
                pool -> pool.getTotalStats().getLeased(),
                LongStream::sum,
                connectionManager);
        DialogueInternalWeakReducingGauge.getOrCreate(
                taggedMetrics,
                DialogueClientPoolMetrics.of(taggedMetrics)
                        .size()
                        .clientName(clientName)
                        .state("pending")
                        .buildMetricName(),
                pool -> pool.getTotalStats().getPending(),
                LongStream::sum,
                connectionManager);
    }

    /** Intentionally opaque wrapper type - we don't want people using the inner Apache client directly. */
    public static final class CloseableClient implements Closeable {
        private static final String APACHE = "apache";

        private final String clientName;
        private final CloseableHttpClient apacheClient;
        private final PoolingHttpClientConnectionManager pool;
        private final ResponseLeakDetector leakDetector;

        @Nullable
        private final ExecutorService executor;

        private final Closer closer = Closer.create();

        private CloseableClient(
                CloseableHttpClient apacheClient,
                @Safe String clientName,
                PoolingHttpClientConnectionManager pool,
                IdleConnectionEvictor connectionEvictor,
                TaggedMetricRegistry taggedMetrics,
                ResponseLeakDetector leakDetector,
                @Nullable ExecutorService executor) {
            this.clientName = clientName;
            this.apacheClient = apacheClient;
            this.pool = pool;
            this.leakDetector = leakDetector;
            this.executor = executor;
            closer.register(() -> {
                connectionEvictor.shutdown();
                try {
                    connectionEvictor.awaitTermination(1L, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            connectionEvictor.start();
            closer.register(apacheClient);
            closer.register(pool::shutdown);
            closer.register(DialogueClientMetrics.of(taggedMetrics)
                    .close()
                    .clientName(clientName)
                    .clientType(APACHE)
                    .build()::mark);
        }

        static CloseableClient wrap(
                CloseableHttpClient apacheClient,
                @Safe String clientName,
                PoolingHttpClientConnectionManager pool,
                IdleConnectionEvictor connectionEvictor,
                ClientConfiguration clientConfiguration,
                @Nullable ExecutorService executor) {
            ResponseLeakDetector leakDetector =
                    ResponseLeakDetector.of(clientName, clientConfiguration.taggedMetricRegistry());
            CloseableClient newInstance = new CloseableClient(
                    apacheClient,
                    clientName,
                    pool,
                    connectionEvictor,
                    clientConfiguration.taggedMetricRegistry(),
                    leakDetector,
                    executor);
            log.info(
                    "Created Apache client {} {} {} {}",
                    SafeArg.of("name", clientName),
                    SafeArg.of("client", Integer.toHexString(System.identityHashCode(apacheClient))),
                    UnsafeArg.of("clientConfiguration", clientConfiguration),
                    UnsafeArg.of("executor", executor));
            Meter createMeter = DialogueClientMetrics.of(clientConfiguration.taggedMetricRegistry())
                    .create()
                    .clientName(clientName)
                    .clientType("apache")
                    .build();
            createMeter.mark();
            return newInstance;
        }

        CloseableHttpClient apacheClient() {
            return apacheClient;
        }

        @Override
        public void close() throws IOException {
            if (log.isDebugEnabled()) {
                PoolStats poolStats = pool.getTotalStats();
                log.debug(
                        "ApacheHttpClientChannels#close - {} {} {} {} {}",
                        SafeArg.of("name", clientName),
                        SafeArg.of("client", Integer.toHexString(System.identityHashCode(apacheClient))),
                        SafeArg.of("idle", poolStats.getAvailable()),
                        SafeArg.of("leased", poolStats.getLeased()),
                        SafeArg.of("pending", poolStats.getPending()),
                        new SafeRuntimeException("Exception for stacktrace"));
            }

            // We intentionally don't close the inner apacheClient here as there might be queued requests which still
            // need to execute on this channel. We rely on finalize() to clean up resources (e.g.
            // IdleConnectionEvictor threads) when this CloseableClient is GC'd.
            pool.closeIdleConnections(0, TimeUnit.NANOSECONDS);
        }

        /**
         * {@link Object#finalize()} gets called when this object is GC'd. Overriding finalize is discouraged
         * because if objects are created faster than finalizer threads can process the GC'd objects, then
         * the system OOMs. We think it's safe in this scenario because we expect these Apache clients to be very
         * infrequently constructed. Tritium 0.16.9 also has instrumentation to measure the size of the finalizer queue
         * (https://github.com/palantir/tritium/pull/712).
         */
        @Override
        @SuppressWarnings({"NoFinalizer", "deprecation"})
        protected void finalize() throws Throwable {
            try {
                finalizeApacheClient();
            } finally {
                super.finalize();
            }
        }

        private void finalizeApacheClient() throws IOException {
            if (log.isInfoEnabled()) {
                PoolStats poolStats = pool.getTotalStats();
                log.info(
                        "ApacheHttpClientChannels#finalize - {} {} {} {} {}",
                        SafeArg.of("name", clientName),
                        SafeArg.of("client", Integer.toHexString(System.identityHashCode(apacheClient))),
                        SafeArg.of("idle", poolStats.getAvailable()),
                        SafeArg.of("leased", poolStats.getLeased()),
                        SafeArg.of("pending", poolStats.getPending()));
            }

            // It's important to close the apacheClient object to avoid leaking threads, in
            // particular the idle connection eviction thread from IdleConnectionEvictor, though there may
            // be additional closeable resources.
            closer.close();
        }

        @Override
        public String toString() {
            return "CloseableClient@" + Integer.toHexString(System.identityHashCode(this)) + "{"
                    + "clientName='" + clientName + '\''
                    + ", client=" + apacheClient
                    + ", pool=" + pool
                    + ", leakDetector=" + leakDetector
                    + ", executor=" + executor
                    + '}';
        }
    }

    public static ClientBuilder clientBuilder() {
        return new ClientBuilder();
    }

    public static final class ClientBuilder {

        private static final long DEFAULT_IDLE_CONNECTION_TIMEOUT_MILLIS = 50_000;

        @Nullable
        private ClientConfiguration clientConfiguration;

        @Nullable
        private String clientName;

        @Nullable
        private ExecutorService executor;

        private ClientBuilder() {}

        public ClientBuilder clientConfiguration(ClientConfiguration value) {
            this.clientConfiguration = Preconditions.checkNotNull(value, "ClientConfiguration is required");
            return this;
        }

        /**
         * {@link Safe} loggable identifier used to identify this client instance for instrumentation
         * purposes. While this value does not impact behavior, using a unique value for each client
         * makes it much easier to monitor and debug the RPC stack.
         */
        public ClientBuilder clientName(@Safe String value) {
            this.clientName = Preconditions.checkNotNull(value, "clientName is required");
            return this;
        }

        /**
         * Configures the {@link ExecutorService} used to execute blocking http requests. If no
         * {@link ExecutorService executor} is provided, a singleton will be used. It's strongly
         * recommended that custom executors support tracing-java.
         * Cached executors are the best fit because we use concurrency limiters to bound
         * concurrent requests.
         */
        public ClientBuilder executor(ExecutorService value) {
            this.executor = Preconditions.checkNotNull(value, "ExecutorService is required");
            return this;
        }

        public CloseableClient build() {
            ClientConfiguration conf =
                    Preconditions.checkNotNull(clientConfiguration, "ClientConfiguration is required");
            String name = Preconditions.checkNotNull(clientName, "Client name is required");
            Preconditions.checkArgument(
                    !conf.fallbackToCommonNameVerification(), "fallback-to-common-name-verification is not supported");
            Preconditions.checkArgument(!conf.meshProxy().isPresent(), "Mesh proxy is not supported");

            long socketTimeoutMillis = conf.readTimeout().toMillis();
            if (conf.readTimeout().toMillis() != conf.writeTimeout().toMillis()) {
                log.warn(
                        "Read and write timeouts do not match, The value of the readTimeout {} will be used and write "
                                + "timeout {} will be ignored.",
                        SafeArg.of("readTimeout", conf.readTimeout()),
                        SafeArg.of("writeTimeout", conf.writeTimeout()));
            }
            int connectTimeout = Ints.checkedCast(conf.connectTimeout().toMillis());
            // Most of our servers use a keep-alive timeout of one minute, by using a slightly lower value on the
            // client side we can avoid unnecessary retries due to race conditions when servers close idle connections
            // as clients attempt to use them.
            // If the socket timeout is non-positive (unbounded) we must use the default value.
            long idleConnectionTimeoutMillis = socketTimeoutMillis > 0
                    ? Math.min(DEFAULT_IDLE_CONNECTION_TIMEOUT_MILLIS, socketTimeoutMillis)
                    : DEFAULT_IDLE_CONNECTION_TIMEOUT_MILLIS;
            // Increased from two seconds to 40% of the idle connection timeout because we have strong support for
            // retries
            // and can optimistically avoid expensive connection checks. Failures caused by NoHttpResponseExceptions
            // are possible when the target closes connections prior to this timeout, and can be safely retried.
            int connectionPoolInactivityCheckMillis = (int) (idleConnectionTimeoutMillis / 2.5);

            SocketConfig socketConfig =
                    SocketConfig.custom().setSoKeepAlive(true).build();
            SSLSocketFactory rawSocketFactory = conf.sslSocketFactory();
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    MetricRegistries.instrument(conf.taggedMetricRegistry(), rawSocketFactory, name),
                    new String[] {"TLSv1.2"},
                    supportedCipherSuites(
                            conf.enableGcmCipherSuites()
                                    ? CipherSuites.allCipherSuites()
                                    : CipherSuites.fastCipherSuites(),
                            rawSocketFactory,
                            name),
                    new DefaultHostnameVerifier());

            PoolingHttpClientConnectionManager connectionManager =
                    new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("http", PlainConnectionSocketFactory.getSocketFactory())
                            .register("https", sslSocketFactory)
                            .build());

            setupConnectionPoolMetrics(conf.taggedMetricRegistry(), name, connectionManager);

            connectionManager.setDefaultSocketConfig(socketConfig);
            connectionManager.setMaxTotal(Integer.MAX_VALUE);
            connectionManager.setDefaultMaxPerRoute(Integer.MAX_VALUE);
            connectionManager.setValidateAfterInactivity(connectionPoolInactivityCheckMillis);

            HttpClientBuilder builder = HttpClients.custom()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setSocketTimeout(Ints.checkedCast(socketTimeoutMillis))
                            .setConnectTimeout(connectTimeout)
                            // Don't allow clients to block forever waiting on a connection to become available
                            .setConnectionRequestTimeout(connectTimeout)
                            // Match okhttp, disallow redirects
                            .setRedirectsEnabled(false)
                            .setRelativeRedirectsAllowed(false)
                            // Don't attempt to replace duplicated slashes with a single slash, otherwise
                            // empty path parameters cannot be matched by the server.
                            .setNormalizeUri(false)
                            .build())
                    // Connection pool lifecycle must be managed separately. This allows us to configure a more
                    // precise IdleConnectionEvictor.
                    .setConnectionManagerShared(true)
                    .setConnectionManager(connectionManager)
                    .setRoutePlanner(new SystemDefaultRoutePlanner(null, conf.proxy()))
                    .disableAutomaticRetries()
                    // Must be disabled otherwise connections are not reused when client certificates are provided
                    .disableConnectionState()
                    // Match okhttp behavior disabling cookies
                    .disableCookieManagement()
                    // Dialogue handles content-compression with ContentDecodingChannel
                    .disableContentCompression()
                    .setDefaultCredentialsProvider(NullCredentialsProvider.INSTANCE)
                    .setTargetAuthenticationStrategy(NullAuthenticationStrategy.INSTANCE)
                    .setProxyAuthenticationStrategy(NullAuthenticationStrategy.INSTANCE)
                    .setDefaultAuthSchemeRegistry(
                            RegistryBuilder.<AuthSchemeProvider>create().build());
            conf.proxyCredentials().ifPresent(credentials -> {
                builder.setDefaultCredentialsProvider(new SingleCredentialsProvider(credentials))
                        .setProxyAuthenticationStrategy(ProxyAuthenticationStrategy.INSTANCE)
                        .setDefaultAuthSchemeRegistry(RegistryBuilder.<AuthSchemeProvider>create()
                                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                                .build());
            });

            CloseableHttpClient apacheClient = builder.build();
            IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor(
                    connectionManager,
                    idleConnectionEvictorThreadFactory(name, conf.taggedMetricRegistry()),
                    // Use a shorter check duration than idle connection timeout duration in order to avoid allowing
                    // stale connections to race the server-side timeout.
                    Math.min(idleConnectionTimeoutMillis, 5_000),
                    TimeUnit.MILLISECONDS,
                    idleConnectionTimeoutMillis,
                    TimeUnit.MILLISECONDS);
            return CloseableClient.wrap(apacheClient, name, connectionManager, connectionEvictor, conf, executor);
        }
    }

    private static ThreadFactory idleConnectionEvictorThreadFactory(String clientName, TaggedMetricRegistry metrics) {
        Preconditions.checkNotNull(clientName, "Client name is required");
        Preconditions.checkNotNull(metrics, "TaggedMetricRegistry is required");
        return MetricRegistries.instrument(
                metrics,
                new ThreadFactoryBuilder()
                        .setNameFormat(clientName + "-IdleConnectionEvictor-%d")
                        .setDaemon(true)
                        .build(),
                "DialogueIdleConnectionEvictor");
    }

    /**
     * Filters the given cipher suites (preserving order) to return only those that are actually supported by this JVM.
     * Otherwise {@code SSLSocketImpl#setEnabledCipherSuites} throws and IllegalArgumentException complaining about an
     * "Unsupported ciphersuite" at client construction time!
     */
    private static String[] supportedCipherSuites(
            String[] cipherSuites, SSLSocketFactory socketFactory, String clientName) {
        Set<String> jvmSupported = supportedCipherSuites(socketFactory);
        List<String> enabled = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();

        for (String cipherSuite : cipherSuites) {
            if (jvmSupported.contains(cipherSuite)) {
                enabled.add(cipherSuite);
            } else {
                unsupported.add(cipherSuite);
            }
        }

        if (!unsupported.isEmpty()) {
            log.debug(
                    "Skipping unsupported cipher suites",
                    SafeArg.of("client", clientName),
                    SafeArg.of("numEnabled", enabled.size()),
                    SafeArg.of("numUnsupported", unsupported.size()),
                    SafeArg.of("cipher", unsupported),
                    SafeArg.of("javaVendor", System.getProperty("java.vendor")),
                    SafeArg.of("javaVersion", System.getProperty("java.version")));
        }

        Preconditions.checkState(!enabled.isEmpty(), "Zero supported cipher suites");
        return enabled.toArray(new String[0]);
    }

    private static ImmutableSet<String> supportedCipherSuites(SSLSocketFactory socketFactory) {
        return ImmutableSet.copyOf(socketFactory.getSupportedCipherSuites());
    }

    private static URL url(String uri) {
        try {
            return new URL(uri);
        } catch (MalformedURLException e) {
            throw new SafeIllegalArgumentException("Failed to parse URL", e);
        }
    }

    private enum NullCredentialsProvider implements CredentialsProvider {
        INSTANCE;

        @Override
        public void setCredentials(AuthScope _authscope, Credentials _credentials) {}

        @Override
        @Nullable
        public Credentials getCredentials(AuthScope _authscope) {
            return null;
        }

        @Override
        public void clear() {}
    }

    private static final class SingleCredentialsProvider implements CredentialsProvider {
        private final Credentials credentials;

        SingleCredentialsProvider(BasicCredentials basicCredentials) {
            credentials = new UsernamePasswordCredentials(basicCredentials.username(), basicCredentials.password());
        }

        @Override
        public void setCredentials(AuthScope _authscope, Credentials _credentials) {}

        @Override
        public Credentials getCredentials(AuthScope _authscope) {
            return credentials;
        }

        @Override
        public void clear() {}
    }

    private enum NullAuthenticationStrategy implements AuthenticationStrategy {
        INSTANCE;

        @Override
        public boolean isAuthenticationRequested(HttpHost _authhost, HttpResponse _response, HttpContext _context) {
            return false;
        }

        @Override
        public Map<String, Header> getChallenges(HttpHost _authhost, HttpResponse _response, HttpContext _context) {
            return Collections.emptyMap();
        }

        @Override
        public Queue<AuthOption> select(
                Map<String, Header> _challenges, HttpHost _authhost, HttpResponse _response, HttpContext _context) {
            return new ArrayDeque<>(1);
        }

        @Override
        public void authSucceeded(HttpHost _authhost, AuthScheme _authScheme, HttpContext _context) {}

        @Override
        public void authFailed(HttpHost _authhost, AuthScheme _authScheme, HttpContext _context) {}
    }
}
