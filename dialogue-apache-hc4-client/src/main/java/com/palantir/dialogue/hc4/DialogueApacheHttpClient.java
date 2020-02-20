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

import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.client.config.CipherSuites;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.blocking.BlockingChannelAdapter;
import com.palantir.dialogue.core.DialogueConfig;
import com.palantir.dialogue.core.HttpChannelFactory;
import com.palantir.dialogue.core.Listenable;
import com.palantir.dialogue.core.SharedResources;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
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

public enum DialogueApacheHttpClient implements HttpChannelFactory {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(DialogueApacheHttpClient.class);
    private static final String STORE = "DialogueApacheHttpClient";

    @Override
    public Channel construct(String uri, Listenable<DialogueConfig> config, SharedResources sharedResources) {
        CloseableHttpClient client = sharedResources
                .getStore(STORE)
                .getOrComputeIfAbsent(
                        "one-off-client-construction",
                        unused -> {
                            return constructLiveReloadingClient(config); // we'll re-use this instance every time
                        },
                        CloseableHttpClient.class);

        return BlockingChannelAdapter.of(new ApacheHttpClientBlockingChannel(client, url(uri)));
    }

    private static CloseableHttpClient constructLiveReloadingClient(Listenable<DialogueConfig> listenable) {
        ConfigurationSubset params = deriveSubsetWeCareAbout(listenable.getListenableCurrentValue());

        listenable.subscribe(() -> {
            ConfigurationSubset newParams = deriveSubsetWeCareAbout(listenable.getListenableCurrentValue());
            if (params.equals(newParams)) {
                // this means users changed something which is irrelevant to us (e.g. a url)
                return;
            }

            log.warn(
                    "Unable to live-reload some configuration changes, ignoring them and using the old configuration "
                            + "{} {}",
                    SafeArg.of("old", params),
                    SafeArg.of("new", newParams));
        });

        return createCloseableHttpClient(params);
    }

    private static ConfigurationSubset deriveSubsetWeCareAbout(DialogueConfig config) {
        ClientConfiguration legacyConf = config.legacyClientConfiguration;

        ConfigurationSubset conf = new ConfigurationSubset();
        conf.connectTimeout = legacyConf.connectTimeout();
        conf.readTimeout = legacyConf.readTimeout();
        conf.writeTimeout = legacyConf.writeTimeout();
        conf.sslSocketFactory = legacyConf.sslSocketFactory();
        conf.enableGcmCipherSuites = legacyConf.enableGcmCipherSuites();
        conf.fallbackToCommonNameVerification = legacyConf.fallbackToCommonNameVerification();
        conf.meshProxy = legacyConf.meshProxy();
        conf.proxy = legacyConf.proxy();
        conf.proxyCredentials = legacyConf.proxyCredentials();
        return conf;
    }

    private static CloseableHttpClient createCloseableHttpClient(ConfigurationSubset conf) {
        log.info("Constructing ClosableHttpClient with conf {}", UnsafeArg.of("conf", conf));
        Preconditions.checkArgument(
                !conf.fallbackToCommonNameVerification, "fallback-to-common-name-verification is not supported");
        Preconditions.checkArgument(!conf.meshProxy.isPresent(), "Mesh proxy is not supported");

        long socketTimeoutMillis = Math.max(conf.readTimeout.toMillis(), conf.writeTimeout.toMillis());
        int connectTimeout = Ints.checkedCast(conf.connectTimeout.toMillis());

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
                .setMaxConnPerRoute(1000)
                .setMaxConnTotal(Integer.MAX_VALUE)
                .setRoutePlanner(new SystemDefaultRoutePlanner(null, conf.proxy))
                .disableAutomaticRetries()
                // Must be disabled otherwise connections are not reused when client certificates are provided
                .disableConnectionState()
                // Match okhttp behavior disabling cookies
                .disableCookieManagement()
                // Dialogue handles content-compression with ContentDecodingChannel
                .disableContentCompression()
                .setSSLSocketFactory(
                        new SSLConnectionSocketFactory(
                                conf.sslSocketFactory,
                                new String[] {"TLSv1.2"},
                                conf.enableGcmCipherSuites
                                        ? CipherSuites.allCipherSuites()
                                        : CipherSuites.fastCipherSuites(),
                                new DefaultHostnameVerifier()))
                .setDefaultCredentialsProvider(NullCredentialsProvider.INSTANCE)
                .setTargetAuthenticationStrategy(NullAuthenticationStrategy.INSTANCE)
                .setProxyAuthenticationStrategy(NullAuthenticationStrategy.INSTANCE)
                .setDefaultAuthSchemeRegistry(
                        RegistryBuilder.<AuthSchemeProvider>create().build());

        conf.proxyCredentials.ifPresent(credentials -> {
            builder.setDefaultCredentialsProvider(new SingleCredentialsProvider(credentials))
                    .setProxyAuthenticationStrategy(ProxyAuthenticationStrategy.INSTANCE)
                    .setDefaultAuthSchemeRegistry(RegistryBuilder.<AuthSchemeProvider>create()
                            .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                            .build());
        });

        CloseableHttpClient build = builder.build();
        // resources will be closed by the 'SharedResources' class
        return build;
    }

    // can't use immutables because intellij complains of a cycles
    static final class ConfigurationSubset {
        private Duration connectTimeout;

        private Duration readTimeout;

        private Duration writeTimeout;

        private boolean enableGcmCipherSuites;

        private boolean fallbackToCommonNameVerification;

        private Optional<BasicCredentials> proxyCredentials;

        private Optional<HostAndPort> meshProxy;

        private ProxySelector proxy;

        private SSLSocketFactory sslSocketFactory;

        @Override
        @SuppressWarnings("CyclomaticComplexity")
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            ConfigurationSubset that = (ConfigurationSubset) obj;
            return enableGcmCipherSuites == that.enableGcmCipherSuites
                    && fallbackToCommonNameVerification == that.fallbackToCommonNameVerification
                    && connectTimeout.equals(that.connectTimeout)
                    && readTimeout.equals(that.readTimeout)
                    && writeTimeout.equals(that.writeTimeout)
                    && proxyCredentials.equals(that.proxyCredentials)
                    && meshProxy.equals(that.meshProxy)
                    && proxy.equals(that.proxy)
                    && sslSocketFactory.equals(that.sslSocketFactory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    connectTimeout,
                    readTimeout,
                    writeTimeout,
                    enableGcmCipherSuites,
                    fallbackToCommonNameVerification,
                    proxyCredentials,
                    meshProxy,
                    proxy,
                    sslSocketFactory);
        }

        @Override
        public String toString() {
            return "ConfigurationSubset{"
                    + "connectTimeout="
                    + connectTimeout
                    + ", readTimeout="
                    + readTimeout
                    + ", writeTimeout="
                    + writeTimeout
                    + ", enableGcmCipherSuites="
                    + enableGcmCipherSuites
                    + ", fallbackToCommonNameVerification="
                    + fallbackToCommonNameVerification
                    + ", proxyCredentials="
                    + proxyCredentials
                    + ", meshProxy="
                    + meshProxy
                    + ", proxy="
                    + proxy
                    + ", sslSocketFactory="
                    + sslSocketFactory
                    + '}';
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
