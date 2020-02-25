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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.CipherSuites;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.blocking.BlockingChannelAdapter;
import com.palantir.dialogue.core.Channels;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
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
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApacheHttpClientChannels {
    private static final Logger log = LoggerFactory.getLogger(ApacheHttpClientChannels.class);

    private ApacheHttpClientChannels() {}

    public static Channel create(ClientConfiguration conf, UserAgent baseAgent) {
        Preconditions.checkArgument(
                !conf.fallbackToCommonNameVerification(), "fallback-to-common-name-verification is not supported");
        Preconditions.checkArgument(!conf.meshProxy().isPresent(), "Mesh proxy is not supported");

        long socketTimeoutMillis =
                Math.max(conf.readTimeout().toMillis(), conf.writeTimeout().toMillis());
        int connectTimeout = Ints.checkedCast(conf.connectTimeout().toMillis());
        // TODO(ckozak): close resources?
        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setSocketTimeout(Ints.checkedCast(socketTimeoutMillis))
                        .setConnectTimeout(connectTimeout)
                        // Don't allow clients to block forever waiting on a connection to become available
                        .setConnectionRequestTimeout(connectTimeout)
                        // Match okhttp, disallow redirects
                        .setRedirectsEnabled(false)
                        .setRelativeRedirectsAllowed(false)
                        .build())
                .setDefaultSocketConfig(
                        SocketConfig.custom().setSoKeepAlive(true).build())
                .evictIdleConnections(55, TimeUnit.SECONDS)
                .setMaxConnPerRoute(Integer.MAX_VALUE)
                .setMaxConnTotal(Integer.MAX_VALUE)
                // TODO(ckozak): proxy credentials
                .setRoutePlanner(new SystemDefaultRoutePlanner(null, conf.proxy()))
                .disableAutomaticRetries()
                // Must be disabled otherwise connections are not reused when client certificates are provided
                .disableConnectionState()
                // Match okhttp behavior disabling cookies
                .disableCookieManagement()
                // Dialogue handles content-compression with ContentDecodingChannel
                .disableContentCompression()
                .setSSLSocketFactory(
                        new SSLConnectionSocketFactory(
                                conf.sslSocketFactory(),
                                new String[] {"TLSv1.2"},
                                conf.enableGcmCipherSuites()
                                        ? jvmSupportedCipherSuites(CipherSuites.allCipherSuites())
                                        : jvmSupportedCipherSuites(CipherSuites.fastCipherSuites()),
                                new DefaultHostnameVerifier()))
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
        CloseableHttpClient client = builder.build();
        ImmutableList<Channel> channels = conf.uris().stream()
                .map(uri -> BlockingChannelAdapter.of(new ApacheHttpClientBlockingChannel(client, url(uri))))
                .collect(ImmutableList.toImmutableList());

        return Channels.create(channels, baseAgent, conf);
    }

    /**
     * Filters the given cipher suites (preserving order) to return only those that are actually supported by this JVM.
     * Otherwise {@code SSLSocketImpl#setEnabledCipherSuites} throws and IllegalArgumentException complaining about an
     * "Unsupported ciphersuite" at client construction time!
     */
    private static String[] jvmSupportedCipherSuites(String[] cipherSuites) {
        Set<String> jvmSupported = jvmSupportedCipherSuites();
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
            log.info(
                    "Skipping unsupported cipher suites",
                    SafeArg.of("numEnabled", enabled.size()),
                    SafeArg.of("numUnsupported", unsupported.size()),
                    SafeArg.of("cipher", unsupported),
                    SafeArg.of("javaVendor", System.getProperty("java.vendor")),
                    SafeArg.of("javaVersion", System.getProperty("java.version")));
        }

        Preconditions.checkState(!enabled.isEmpty(), "Zero supported cipher suites");
        return enabled.toArray(new String[0]);
    }

    private static ImmutableSet<String> jvmSupportedCipherSuites() {
        try {
            SSLSocketFactory socketFactory = SSLContext.getDefault().getSocketFactory();
            return ImmutableSet.copyOf(socketFactory.getSupportedCipherSuites());
        } catch (NoSuchAlgorithmException e) {
            throw new SafeRuntimeException("Unable to determine JVM supported cipher suites", e);
        }
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
