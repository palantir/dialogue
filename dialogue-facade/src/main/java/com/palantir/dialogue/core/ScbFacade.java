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

package com.palantir.dialogue.core;

import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfigurationFactory;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.security.Provider;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.immutables.value.Value;

public final class ScbFacade {

    private final ImmutableParams2 params;
    private final ApacheCache cache;

    ScbFacade(Params2 params) {
        this.params = ImmutableParams2.builder().from(params).build();
        this.cache = new ApacheCache();
    }

    private ScbFacade(Params2 params, ApacheCache cache) {
        this.params = ImmutableParams2.builder().from(params).build();
        this.cache = cache;
    }

    ScbFacade withUserAgent(UserAgent userAgent) {
        return new ScbFacade(params.withUserAgent(userAgent), cache);
    }

    ScbFacade withMaxNumRetries(int value) {
        return new ScbFacade(params.withMaxNumRetries(value), cache);
    }

    // TODO(dfox): expose more 'with' functions

    /**
     * LIMITATIONS:
     * <ul>
     *     <li>Doesn't do fancy granular live-reload, i.e. throw away all the old concurrency limiter state
     * </ul>
     */
    public <T> T get(Class<T> serviceClass, String serviceName) {
        AtomicReference<Channel> atomic = PollingRefreshable.map(params.scb(), params.executor(), block -> {
            if (!block.services().containsKey(serviceName)) {
                return new AlwaysThrowing(() -> new SafeIllegalStateException(
                        "Service not configured",
                        SafeArg.of("serviceName", serviceName),
                        SafeArg.of("available", block.services().keySet())));
            }

            ServiceConfigurationFactory configFactory = ServiceConfigurationFactory.of(block);
            ServiceConfiguration serviceConf = configFactory.get(serviceName);
            ServiceConfiguration stripUris = ServiceConfiguration.builder()
                    .from(serviceConf)
                    .uris(Collections.emptyList())
                    .build();
            ApacheCache.CacheEntry entry = cache.get(ImmutableGetApacheClient.builder()
                    .serviceName(serviceName)
                    .params(params)
                    .serviceConf(stripUris)
                    .build());

            ClientConfiguration restoreUris = ClientConfiguration.builder()
                    .from(entry.conf())
                    .uris(serviceConf.uris())
                    .build();
            return new BasicBuilder()
                    .channelName("scb-facade-" + serviceName) // TODO(dfox): append more
                    .clientConfiguration(restoreUris)
                    .channelFactory(uri -> ApacheHttpClientChannels.createSingleUri(uri, entry.client()))
                    .scheduler(params.executor())
                    .build();
        });
        AtomicChannel channel = new AtomicChannel(atomic);
        return Facade.callStaticFactoryMethod(serviceClass, channel, params.runtime());
    }

    @Value.Immutable
    interface Params2 extends Facade.BaseParams {

        Supplier<ServicesConfigBlock> scb();

        @Value.Default
        default TaggedMetricRegistry taggedMetrics() {
            return SharedTaggedMetricRegistries.getSingleton();
        }

        Optional<UserAgent> userAgent();

        Optional<NodeSelectionStrategy> nodeSelectionStrategy();

        Optional<Duration> failedUrlCooldown();

        Optional<ClientConfiguration.ClientQoS> clientQoS();

        Optional<ClientConfiguration.ServerQoS> serverQoS();

        Optional<ClientConfiguration.RetryOnTimeout> retryOnTimeout();

        Optional<Provider> securityProvider();

        /**
         * The provided value will only be respected if the corresponding field in {@link ServiceConfiguration} is absent.
         */
        Optional<Integer> maxNumRetries();
    }

    static ClientConfiguration getClientConfig(ServiceConfiguration clientConfig, Params2 ps) {
        ClientConfiguration.Builder builder = ClientConfiguration.builder()
                .from(ClientConfigurations.of(clientConfig))
                .userAgent(ps.userAgent())
                .taggedMetricRegistry(ps.taggedMetrics());

        ps.securityProvider()
                .ifPresent(provider -> builder.sslSocketFactory(
                        SslSocketFactories.createSslSocketFactory(clientConfig.security(), provider)));
        ps.nodeSelectionStrategy().ifPresent(builder::nodeSelectionStrategy);
        ps.failedUrlCooldown().ifPresent(builder::failedUrlCooldown);
        ps.clientQoS().ifPresent(builder::clientQoS);
        ps.serverQoS().ifPresent(builder::serverQoS);
        ps.retryOnTimeout().ifPresent(builder::retryOnTimeout);

        if (!clientConfig.maxNumRetries().isPresent()) {
            ps.maxNumRetries().ifPresent(builder::maxNumRetries);
        }
        return builder.build();
    }
}
