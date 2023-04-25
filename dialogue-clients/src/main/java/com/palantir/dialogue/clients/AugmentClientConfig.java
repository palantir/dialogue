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

package com.palantir.dialogue.clients;

import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.ClientConfigurations.TrustContextFactory;
import com.palantir.conjure.java.client.config.HostEventsSink;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.config.ssl.TrustContext;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.security.Provider;
import java.util.Optional;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.immutables.value.Value;

/**
 * Internal API. Options that users might want to override when turning a {@link ServiceConfiguration} into a
 * {@link ClientConfiguration}.
 */
interface AugmentClientConfig {
    @Value.Default
    @SuppressWarnings("deprecation") // ideally users would wire in a registry, but this works out of the box
    default TaggedMetricRegistry taggedMetrics() {
        return SharedTaggedMetricRegistries.getSingleton();
    }

    Optional<UserAgent> userAgent();

    Optional<NodeSelectionStrategy> nodeSelectionStrategy();

    Optional<ClientConfiguration.ClientQoS> clientQoS();

    Optional<ClientConfiguration.ServerQoS> serverQoS();

    Optional<ClientConfiguration.RetryOnTimeout> retryOnTimeout();

    Optional<Provider> securityProvider();

    /**
     * The provided value will only be respected if the corresponding field in {@link ServiceConfiguration}
     * is absent.
     */
    Optional<Integer> maxNumRetries();

    Optional<HostEventsSink> hostEventsSink();

    static ClientConfiguration getClientConf(ServiceConfiguration serviceConfig, AugmentClientConfig augment) {
        TrustContextFactory trustContextFactory = buildTrustContextFactory(augment.securityProvider());
        ClientConfiguration.Builder builder =
                ClientConfiguration.builder().from(ClientConfigurations.of(serviceConfig, trustContextFactory));

        if (serviceConfig.maxNumRetries().isEmpty()) {
            augment.maxNumRetries().ifPresent(builder::maxNumRetries);
        }

        if (augment.securityProvider().isPresent()) {
            // Opt into GCM when custom providers (Conscrypt) is used.
            builder.enableGcmCipherSuites(true);
        }

        builder.userAgent(augment.userAgent());
        builder.taggedMetricRegistry(augment.taggedMetrics());

        augment.nodeSelectionStrategy().ifPresent(builder::nodeSelectionStrategy);
        augment.clientQoS().ifPresent(builder::clientQoS);
        augment.serverQoS().ifPresent(builder::serverQoS);
        augment.retryOnTimeout().ifPresent(builder::retryOnTimeout);
        augment.hostEventsSink().ifPresent(builder::hostEventsSink);

        return builder.build();
    }

    private static TrustContextFactory buildTrustContextFactory(Optional<Provider> securityProvider) {
        return sslConfiguration -> {
            TrustManager[] trustManagers = SslSocketFactories.createTrustManagers(sslConfiguration);
            KeyManager[] keyManagers = SslSocketFactories.createKeyManagers(sslConfiguration);

            SSLContext sslContext;
            if (securityProvider.isPresent()) {
                sslContext = SslSocketFactories.createSslContext(trustManagers, keyManagers, securityProvider.get());
            } else {
                sslContext = SslSocketFactories.createSslContext(trustManagers, keyManagers);
            }

            // Reduce the session cache size for clients. We expect TLS connections to be reused, thus the cache isn't
            // terribly important.
            sslContext.getClientSessionContext().setSessionCacheSize(100);

            return TrustContext.of(
                    sslContext.getSocketFactory(), SslSocketFactories.getX509TrustManager(trustManagers));
        };
    }
}
