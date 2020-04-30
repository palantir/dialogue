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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.security.Provider;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.immutables.value.Value;

public final class ScbFacade {

    private final ImmutableParams2 params;

    ScbFacade(Params2 params) {
        this.params = ImmutableParams2.builder().from(params).build();
    }

    ScbFacade withUserAgent(UserAgent userAgent) {
        return new ScbFacade(params.withUserAgent(userAgent));
    }

    ScbFacade withMaxNumRetries(int value) {
        return new ScbFacade(params.withMaxNumRetries(value));
    }

    // TODO(dfox): expose more 'with' functions

    <T> T get(Class<T> serviceClass, String serviceName) {
        AtomicReference<Channel> atomic = PollingRefreshable.map(params.scb(), params.executor(), block -> {
            if (!block.services().containsKey(serviceName)) {
                return new AlwaysThrowing(() -> new SafeIllegalStateException(
                        "Service not configured",
                        SafeArg.of("serviceName", serviceName),
                        SafeArg.of("available", block.services().keySet())));
            }

            ServiceConfigurationFactory configFactory = ServiceConfigurationFactory.of(block);
            ServiceConfiguration serviceConf = configFactory.get(serviceName);
            ClientConfiguration clientConf = getClientConfig(serviceConf);

            Facade facade = new Facade(params);
            return facade.getChannel("facade2-reloading-" + serviceName, clientConf);
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

    private ClientConfiguration getClientConfig(ServiceConfiguration clientConfig) {
        ClientConfiguration.Builder builder = ClientConfiguration.builder()
                .from(ClientConfigurations.of(clientConfig))
                .userAgent(params.userAgent())
                .taggedMetricRegistry(params.taggedMetrics());

        params.securityProvider()
                .ifPresent(provider -> builder.sslSocketFactory(
                        SslSocketFactories.createSslSocketFactory(clientConfig.security(), provider)));
        params.nodeSelectionStrategy().ifPresent(builder::nodeSelectionStrategy);
        params.failedUrlCooldown().ifPresent(builder::failedUrlCooldown);
        params.clientQoS().ifPresent(builder::clientQoS);
        params.serverQoS().ifPresent(builder::serverQoS);
        params.retryOnTimeout().ifPresent(builder::retryOnTimeout);

        if (!clientConfig.maxNumRetries().isPresent()) {
            params.maxNumRetries().ifPresent(builder::maxNumRetries);
        }
        return builder.build();
    }
}
