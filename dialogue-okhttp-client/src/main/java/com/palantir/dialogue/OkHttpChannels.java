/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.CipherSuites;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.core.Channels;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.TlsVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OkHttpChannels {

    private static final Logger log = LoggerFactory.getLogger(OkHttpChannels.class);
    private static final boolean DEFAULT_ENABLE_HTTP2 = true;

    private static final ThreadFactory executionThreads = new ThreadFactoryBuilder()
            .setUncaughtExceptionHandler((thread, uncaughtException) -> log.error(
                    "An exception was uncaught in an execution thread. "
                            + "This likely left a thread blocked, and is as such a serious bug "
                            + "which requires debugging.",
                    uncaughtException))
            .setNameFormat("remoting-okhttp-dispatcher-%d")
            // This diverges from the OkHttp default value, allowing the JVM to cleanly exit
            // while idle dispatcher threads are still alive.
            .setDaemon(true)
            .build();

    /**
     * The {@link ExecutorService} used for the {@link Dispatcher}s of all OkHttp clients created through this class.
     * Similar to OkHttp's default, but with two modifications:
     *
     * <ol>
     *   <li>A logging uncaught exception handler
     *   <li>Daemon threads: active request will not block JVM shutdown <b>unless</b> another non-daemon thread blocks
     *       waiting for the result. Most of our usage falls into this category. This allows JVM shutdown to occur
     *       cleanly without waiting a full minute after the last request completes.
     * </ol>
     */
    private static final ExecutorService executionExecutor = Executors.newCachedThreadPool(executionThreads);

    /** Shared dispatcher with static executor service. */
    private static final Dispatcher dispatcher;

    static {
        dispatcher = new Dispatcher(executionExecutor);
        // Restricting concurrency is done elsewhere in ConcurrencyLimiters.
        dispatcher.setMaxRequests(Integer.MAX_VALUE);
        // Must be less than maxRequests so a single slow host does not block all requests
        dispatcher.setMaxRequestsPerHost(256);
    }

    /** Shared connection pool. */
    private static final ConnectionPool connectionPool = new ConnectionPool(
            1000,
            // Most servers use a one minute keepalive for idle connections, by using a shorter keepalive on
            // clients we can avoid race conditions where the attempts to reuse a connection as the server
            // closes it, resulting in unnecessary I/O exceptions and retrial.
            55,
            TimeUnit.SECONDS);

    private OkHttpChannels() {}

    public static Channel create(ClientConfiguration config, UserAgent baseAgent) {
        Preconditions.checkArgument(
                !config.fallbackToCommonNameVerification(), "fallback-to-common-name-verification is not supported");
        Preconditions.checkArgument(!config.meshProxy().isPresent(), "Mesh proxy is not supported");
        OkHttpClient.Builder builder = new OkHttpClient()
                .newBuilder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .followRedirects(false) // We implement our own redirect logic.
                .sslSocketFactory(config.sslSocketFactory(), config.trustManager())
                // timeouts
                .connectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.writeTimeout().toMillis(), TimeUnit.MILLISECONDS)
                // proxy
                .proxySelector(config.proxy())
                .retryOnConnectionFailure(false);

        if (config.proxyCredentials().isPresent()) {
            BasicCredentials basicCreds = config.proxyCredentials().get();
            final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
            builder.proxyAuthenticator((route, response) -> response.request()
                    .newBuilder()
                    .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                    .build());
        }

        // cipher setup
        builder.connectionSpecs(createConnectionSpecs(config.enableGcmCipherSuites()));
        // gcm ciphers are required for http/2 per https://tools.ietf.org/html/rfc7540#section-9.2.2
        // some servers fail to implement this piece of the specification, which can violate our
        // assumptions.
        // This check can be removed once we've migrated to TLSv1.3+
        if (!config.enableGcmCipherSuites() || !config.enableHttp2().orElse(DEFAULT_ENABLE_HTTP2)) {
            builder.protocols(ImmutableList.of(Protocol.HTTP_1_1));
        }

        OkHttpClient client = builder.build();
        ImmutableList<Channel> channels = config.uris().stream()
                .map(uri -> OkHttpChannel.of(client, url(uri)))
                .collect(ImmutableList.toImmutableList());

        return Channels.create(channels, baseAgent, config);
    }

    private static URL url(String uri) {
        try {
            return new URL(uri);
        } catch (MalformedURLException e) {
            throw new SafeIllegalArgumentException("Failed to parse URL", e);
        }
    }

    private static ImmutableList<ConnectionSpec> createConnectionSpecs(boolean enableGcmCipherSuites) {
        return ImmutableList.of(
                new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .cipherSuites(
                                enableGcmCipherSuites
                                        ? CipherSuites.allCipherSuites()
                                        : CipherSuites.fastCipherSuites())
                        .build(),
                ConnectionSpec.CLEARTEXT);
    }
}
