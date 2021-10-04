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
package com.palantir.dialogue.hc5;

import com.codahale.metrics.Meter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.client.config.CipherSuites;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.dialogue.blocking.BlockingChannelAdapter;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.core.DialogueChannelFactory;
import com.palantir.dialogue.core.DialogueInternalWeakReducingGauge;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public final class ApacheHttpClientChannels {
    private static final SafeLogger log = SafeLoggerFactory.get(ApacheHttpClientChannels.class);
    private static final String CLIENT_TYPE = "apache-hc5";
    // Starting conservatively matching the default conjure connect timeout.
    // This value acts as a minimum timeout when a low connect timeout is configured
    // to prevent handshakes from causing retry storms that burn CPU.
    @VisibleForTesting
    static final Timeout DEFAULT_HANDSHAKE_TIMEOUT = Timeout.ofSeconds(10);

    private ApacheHttpClientChannels() {}

    /**
     * Simple cjr-style factory method. Prefer using {@code dialogue-clients} general purpose factory where possible.
     * This method exists to ease of migration from hc4 to hc5.
     *
     * @deprecated Prefer dialogue-clients if possible, otherwise please provide a channel name for more accurate
     * metrics using {@link #create(ClientConfiguration, String)}.
     */
    @Deprecated
    public static Channel create(ClientConfiguration conf) {
        return create(conf, "apache-channel");
    }

    /**
     * Simple cjr-style factory method. Prefer using {@code dialogue-clients} general purpose factory where possible.
     * This method exists to ease of migration from hc4 to hc5.
     */
    public static Channel create(ClientConfiguration conf, String channelName) {
        CloseableClient client = createCloseableHttpClient(conf, channelName);
        return DialogueChannel.builder()
                .channelName(channelName)
                .clientConfiguration(conf)
                .factory(args -> createSingleUri(args, client))
                .build();
    }

    public static Channel createSingleUri(DialogueChannelFactory.ChannelArgs args, CloseableClient client) {
        BlockingChannel blockingChannel = new ApacheHttpClientBlockingChannel(
                client, url(args.uri()), client.leakDetector(), args.uriIndexForInstrumentation());
        return client.executor() == null
                ? BlockingChannelAdapter.of(blockingChannel)
                : BlockingChannelAdapter.of(blockingChannel, client.executor());
    }

    public static Channel createSingleUri(String uri, CloseableClient client) {
        return createSingleUri(
                DialogueChannelFactory.ChannelArgs.builder().uri(uri).build(), client);
    }

    public static CloseableClient createCloseableHttpClient(ClientConfiguration conf, String clientName) {
        return clientBuilder().clientConfiguration(conf).clientName(clientName).build();
    }

    private static void setupConnectionPoolMetrics(
            TaggedMetricRegistry taggedMetrics,
            String clientName,
            PoolingHttpClientConnectionManager connectionManager) {
        DialogueClientPoolMetrics metrics = DialogueClientPoolMetrics.of(taggedMetrics);
        DialogueInternalWeakReducingGauge.getOrCreate(
                taggedMetrics,
                metrics.size().clientName(clientName).state("idle").buildMetricName(),
                pool -> pool.getTotalStats().getAvailable(),
                LongStream::sum,
                connectionManager);
        DialogueInternalWeakReducingGauge.getOrCreate(
                taggedMetrics,
                metrics.size().clientName(clientName).state("leased").buildMetricName(),
                pool -> pool.getTotalStats().getLeased(),
                LongStream::sum,
                connectionManager);
        DialogueInternalWeakReducingGauge.getOrCreate(
                taggedMetrics,
                metrics.size().clientName(clientName).state("pending").buildMetricName(),
                pool -> pool.getTotalStats().getPending(),
                LongStream::sum,
                connectionManager);
    }

    public abstract static class CloseableClient implements Closeable {

        static CloseableClient wrap(
                CloseableHttpClient apacheClient,
                @Safe String clientName,
                PoolingHttpClientConnectionManager pool,
                ScheduledFuture<?> connectionEvictorFuture,
                ClientConfiguration clientConfiguration,
                @Nullable ExecutorService executor) {
            ResponseLeakDetector leakDetector =
                    ResponseLeakDetector.of(clientName, clientConfiguration.taggedMetricRegistry());
            CloseableClientImpl newInstance = new CloseableClientImpl(
                    apacheClient,
                    clientName,
                    pool,
                    connectionEvictorFuture,
                    leakDetector,
                    executor,
                    clientConfiguration);
            if (log.isDebugEnabled()) {
                // If debug is enabled, log the stack trace.
                log.debug(
                        "Created Apache client {} {} {} {}",
                        SafeArg.of("name", clientName),
                        SafeArg.of("client", Integer.toHexString(System.identityHashCode(apacheClient))),
                        UnsafeArg.of("clientConfiguration", clientConfiguration),
                        UnsafeArg.of("executor", executor),
                        new SafeRuntimeException("Created here"));
            } else {
                log.info(
                        "Created Apache client {} {} {} {}",
                        SafeArg.of("name", clientName),
                        SafeArg.of("client", Integer.toHexString(System.identityHashCode(apacheClient))),
                        UnsafeArg.of("clientConfiguration", clientConfiguration),
                        UnsafeArg.of("executor", executor));
            }
            Meter createMeter = DialogueClientMetrics.of(clientConfiguration.taggedMetricRegistry())
                    .create()
                    .clientName(clientName)
                    .clientType(CLIENT_TYPE)
                    .build();
            createMeter.mark();
            CloseableClient wrapper = new CloseableClientWrapper(newInstance);
            CleanerSupport.register(wrapper, newInstance::closeApacheClient);
            return wrapper;
        }

        abstract CloseableHttpClient apacheClient();

        abstract ClientConfiguration clientConfiguration();

        abstract String name();

        @Nullable
        abstract ExecutorService executor();

        abstract ResponseLeakDetector leakDetector();
    }

    private static final class CloseableClientWrapper extends CloseableClient {

        private final CloseableClient delegate;

        CloseableClientWrapper(CloseableClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public CloseableHttpClient apacheClient() {
            return delegate.apacheClient();
        }

        @Override
        public ClientConfiguration clientConfiguration() {
            return delegate.clientConfiguration();
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        @Nullable
        ExecutorService executor() {
            return delegate.executor();
        }

        @Override
        ResponseLeakDetector leakDetector() {
            return delegate.leakDetector();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public String toString() {
            return "CloseableClientWrapper{" + delegate + '}';
        }
    }

    /** Intentionally opaque wrapper type - we don't want people using the inner Apache client directly. */
    private static final class CloseableClientImpl extends CloseableClient {

        private final String clientName;
        private final CloseableHttpClient apacheClient;
        private final PoolingHttpClientConnectionManager pool;
        private final ResponseLeakDetector leakDetector;
        private final ClientConfiguration clientConfiguration;

        @Nullable
        private final ExecutorService executor;

        private final Closer closer = Closer.create();

        private CloseableClientImpl(
                CloseableHttpClient apacheClient,
                @Safe String clientName,
                PoolingHttpClientConnectionManager pool,
                ScheduledFuture<?> connectionEvictorFuture,
                ResponseLeakDetector leakDetector,
                @Nullable ExecutorService executor,
                ClientConfiguration clientConfiguration) {
            this.clientName = clientName;
            this.apacheClient = apacheClient;
            this.pool = pool;
            this.leakDetector = leakDetector;
            this.executor = executor;
            this.clientConfiguration = clientConfiguration;
            closer.register(() -> connectionEvictorFuture.cancel(true));
            closer.register(apacheClient);
            closer.register(pool);
            closer.register(DialogueClientMetrics.of(clientConfiguration.taggedMetricRegistry())
                    .close()
                    .clientName(clientName)
                    .clientType(CLIENT_TYPE)
                    .build()::mark);
        }

        @Override
        CloseableHttpClient apacheClient() {
            return apacheClient;
        }

        @Override
        ClientConfiguration clientConfiguration() {
            return clientConfiguration;
        }

        @Override
        String name() {
            return clientName;
        }

        @Override
        @Nullable
        ExecutorService executor() {
            return executor;
        }

        @Override
        ResponseLeakDetector leakDetector() {
            return leakDetector;
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
            pool.closeIdle(TimeValue.ZERO_MILLISECONDS);
        }

        private void closeApacheClient() {
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
            try {
                closer.close();
            } catch (IOException e) {
                log.warn("Failed to close client", e);
            }
        }

        @Override
        public String toString() {
            return "CloseableClientImpl@" + Integer.toHexString(System.identityHashCode(this)) + "{"
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

        // Most of our servers use a keep-alive timeout of one minute, by using a slightly lower value on the
        // client side we can avoid unnecessary retries due to race conditions when servers close idle connections
        // as clients attempt to use them.
        // Note that pooled idle connections use an infinite socket timeout so there is no reason to scale
        // this value with configured timeouts.
        private static final Timeout IDLE_CONNECTION_TIMEOUT = Timeout.ofSeconds(50);

        // Increased from two seconds to four seconds because we have strong support for retries
        // and can optimistically avoid expensive connection checks. Failures caused by NoHttpResponseExceptions
        // are possible when the target closes connections prior to this timeout, and can be safely retried.
        // Ideally this value would be larger for RPC, however some servers use relatively low defaults:
        // apache httpd versions 1.3 and 2.0: 15 seconds:
        // https://httpd.apache.org/docs/2.0/mod/core.html#keepalivetimeout
        // apache httpd version 2.2 and above: 5 seconds
        // https://httpd.apache.org/docs/2.2/mod/core.html#keepalivetimeout
        // nodejs http server: 5 seconds
        // https://nodejs.org/api/http.html#http_server_keepalivetimeout
        // nginx: 75 seconds (good)
        // https://nginx.org/en/docs/http/ngx_http_core_module.html#keepalive_timeout
        // dropwizard: 30 seconds (see idleTimeout in the linked docs)
        // https://www.dropwizard.io/en/latest/manual/configuration.html#Connectors
        // wc: 60 seconds (internal)
        private static final TimeValue CONNECTION_INACTIVITY_CHECK = TimeValue.ofMilliseconds(
                Integer.getInteger("dialogue.experimental.inactivity.check.threshold.millis", 4_000));

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

            Timeout socketTimeout = getSocketTimeout(conf, name);

            Timeout connectTimeout = Timeout.ofMilliseconds(
                    Ints.checkedCast(conf.connectTimeout().toMillis()));

            Timeout handshakeTimeout = getHandshakeTimeout(connectTimeout, socketTimeout, name);

            InetSocketAddress socksProxyAddress = getSocksProxyAddress();
            SSLSocketFactory rawSocketFactory = conf.sslSocketFactory();
            SSLConnectionSocketFactory sslSocketFactory =
                    new SSLConnectionSocketFactory(
                            MetricRegistries.instrument(conf.taggedMetricRegistry(), rawSocketFactory, name),
                            TlsProtocols.enabledFor(name),
                            supportedCipherSuites(
                                    conf.enableGcmCipherSuites()
                                            ? CipherSuites.allCipherSuites()
                                            : CipherSuites.fastCipherSuites(),
                                    rawSocketFactory,
                                    name),
                            new DefaultHostnameVerifier()) {
                        @Override
                        public Socket createSocket(final HttpContext context) throws IOException {
                            return socksProxyAddress == null
                                    ? super.createSocket(context)
                                    : new Socket(new Proxy(Proxy.Type.SOCKS, socksProxyAddress));
                        }
                    };

            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register(URIScheme.HTTP.id, new PlainConnectionSocketFactory() {
                                @Override
                                public Socket createSocket(final HttpContext context) throws IOException {
                                    return socksProxyAddress == null
                                            ? super.createSocket(context)
                                            : new Socket(new Proxy(Proxy.Type.SOCKS, socksProxyAddress));
                                }
                            })
                            .register(URIScheme.HTTPS.id, sslSocketFactory)
                            .build(),
                    PoolConcurrencyPolicy.LAX,
                    // Allow unnecessary connections to time out reducing system load.
                    PoolReusePolicy.LIFO,
                    // No maximum time to live
                    TimeValue.NEG_ONE_MILLISECOND,
                    null,
                    new InstrumentedDnsResolver(SystemDefaultDnsResolver.INSTANCE),
                    new InstrumentedManagedHttpConnectionFactory(
                            ManagedHttpClientConnectionFactory.INSTANCE, conf.taggedMetricRegistry(), name));
            connectionManager.setDefaultSocketConfig(SocketConfig.custom()
                    .setSoKeepAlive(true)
                    // The default socket configuration socket timeout only applies prior to request execution.
                    // By using a more specific timeout here, we bound the handshake in addition to the
                    // socket.connect call.
                    .setSoTimeout(handshakeTimeout)
                    // Doesn't appear to do anything in this release
                    .setSocksProxyAddress(socksProxyAddress)
                    .build());
            connectionManager.setValidateAfterInactivity(CONNECTION_INACTIVITY_CHECK);
            connectionManager.setMaxTotal(Integer.MAX_VALUE);
            connectionManager.setDefaultMaxPerRoute(Integer.MAX_VALUE);

            setupConnectionPoolMetrics(conf.taggedMetricRegistry(), name, connectionManager);

            HttpClientBuilder builder = HttpClients.custom()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(connectTimeout)
                            // Don't allow clients to block forever waiting on a connection to become available
                            .setConnectionRequestTimeout(connectTimeout)
                            // The response timeout is used as the socket timeout for the duration of
                            // an exchange.
                            .setResponseTimeout(socketTimeout)
                            // Match okhttp, disallow redirects
                            .setRedirectsEnabled(false)
                            .setAuthenticationEnabled(conf.proxyCredentials().isPresent())
                            .setExpectContinueEnabled(false)
                            .setConnectionKeepAlive(IDLE_CONNECTION_TIMEOUT)
                            .build())
                    // Connection pool lifecycle must be managed separately. This allows us to configure a more
                    // precise IdleConnectionEvictor.
                    .setConnectionManagerShared(true)
                    .setKeepAliveStrategy(
                            new InactivityValidationAwareConnectionKeepAliveStrategy(connectionManager, name))
                    .setConnectionManager(new InstrumentedPoolingHttpClientConnectionManager(
                            connectionManager, conf.taggedMetricRegistry(), name, CLIENT_TYPE))
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
                            RegistryBuilder.<AuthSchemeFactory>create().build());
            conf.proxyCredentials().ifPresent(credentials -> builder.setDefaultCredentialsProvider(
                            new SingleCredentialsProvider(credentials))
                    .setProxyAuthenticationStrategy(DefaultAuthenticationStrategy.INSTANCE)
                    .setDefaultAuthSchemeRegistry(RegistryBuilder.<AuthSchemeFactory>create()
                            .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
                            .build()));

            CloseableHttpClient apacheClient = builder.build();
            ScheduledFuture<?> connectionEvictorFuture =
                    ScheduledIdleConnectionEvictor.schedule(connectionManager, Duration.ofSeconds(5));
            return CloseableClient.wrap(apacheClient, name, connectionManager, connectionEvictorFuture, conf, executor);
        }
    }

    @Nullable
    private static InetSocketAddress getSocksProxyAddress() {
        String rawValue = System.getProperty("dialogue.experimental.socks5.proxy");
        if (Strings.isNullOrEmpty(rawValue)) {
            return null;
        }
        HostAndPort hostAndPort = HostAndPort.fromString(rawValue);
        return InetSocketAddress.createUnresolved(hostAndPort.getHost(), hostAndPort.getPort());
    }

    private static Timeout getSocketTimeout(ClientConfiguration conf, String clientName) {
        long socketTimeoutMillis = conf.readTimeout().toMillis();
        if (conf.readTimeout().toMillis() != conf.writeTimeout().toMillis()) {
            log.info(
                    "Read and write timeouts do not match, The value of the readTimeout {} will be used and write "
                            + "timeout {} will be ignored. Client: {}",
                    SafeArg.of("readTimeout", conf.readTimeout()),
                    SafeArg.of("writeTimeout", conf.writeTimeout()),
                    SafeArg.of("client", clientName));
        }
        if (socketTimeoutMillis == 0) {
            // https://issues.apache.org/jira/browse/HTTPCLIENT-2099
            log.debug(
                    "Working around HTTPCLIENT-2099 by using a 1 day socket "
                            + "timeout instead of zero (unlimited). Client: {}",
                    SafeArg.of("client", clientName));
            socketTimeoutMillis = Duration.ofDays(1).toMillis();
        }
        return Timeout.ofMilliseconds(socketTimeoutMillis);
    }

    @VisibleForTesting
    static Timeout getHandshakeTimeout(Timeout connectTimeout, Timeout socketTimeout, String clientName) {
        if (connectTimeout.isEnabled()) {
            // Use the connect timeout when values are sufficiently high, with a lower boundj
            if (connectTimeout.toMilliseconds() >= DEFAULT_HANDSHAKE_TIMEOUT.toMilliseconds()) {
                return connectTimeout;
            }
            Timeout normalizedTimeout = Timeout.ofMilliseconds(
                    Math.min(socketTimeout.toMilliseconds(), DEFAULT_HANDSHAKE_TIMEOUT.toMilliseconds()));
            log.info(
                    "Handshake timeout for client {} increased to {} from connect and socket timeouts {} and {}",
                    SafeArg.of("client", clientName),
                    SafeArg.of("handshakeTimeout", normalizedTimeout),
                    SafeArg.of("connectTimeout", connectTimeout),
                    SafeArg.of("socketTimeout", socketTimeout));
            return normalizedTimeout;
        }
        return socketTimeout;
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
        @Nullable
        public Credentials getCredentials(AuthScope _authScope, HttpContext _context) {
            return null;
        }
    }

    private static final class SingleCredentialsProvider implements CredentialsProvider {
        private final Credentials credentials;

        SingleCredentialsProvider(BasicCredentials basicCredentials) {
            credentials = new UsernamePasswordCredentials(
                    basicCredentials.username(), basicCredentials.password().toCharArray());
        }

        @Override
        public Credentials getCredentials(AuthScope _authScope, HttpContext _context) {
            return credentials;
        }
    }

    private enum NullAuthenticationStrategy implements AuthenticationStrategy {
        INSTANCE;

        @Override
        public List<AuthScheme> select(
                ChallengeType _challengeType, Map<String, AuthChallenge> _challenges, HttpContext _context) {
            return Collections.emptyList();
        }
    }
}
