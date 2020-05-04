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
import com.palantir.conjure.java.api.config.service.ServiceConfigurationFactory;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.logsafe.Preconditions;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.security.Provider;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.immutables.value.Value;

final class ReloadingClientFactory implements DialogueClients.ReloadingFactory {

    private final ImmutableReloadingParams params;
    private final ChannelCache cache;

    ReloadingClientFactory(ImmutableReloadingParams params, ChannelCache cache) {
        this.params = params;
        this.cache = cache;
    }

    @Value.Immutable
    interface ReloadingParams extends BaseParams {
        Refreshable<ServicesConfigBlock> scb();
    }

    @Override
    public <T> T get(Class<T> serviceClass, String serviceName) {
        Preconditions.checkNotNull(serviceClass, "serviceClass");
        Preconditions.checkNotNull(serviceName, "serviceName");

        Refreshable<Optional<ServiceConfiguration>> mapped = params.scb().map(block -> {
            Preconditions.checkNotNull(block, "Refreshable must not provide a null ServicesConfigBlock");

            if (!block.services().containsKey(serviceName)) {
                return Optional.empty();
            }

            return Optional.of(ServiceConfigurationFactory.of(block).get(serviceName));
        });

        ReloadingSingleClientFactory factory = new ReloadingSingleClientFactory(
                ImmutableParams3.builder()
                        .from(params)
                        .serviceConf(mapped)
                        .serviceName(serviceName)
                        .build(),
                cache);

        return factory.get(serviceClass);
    }

    @Override
    public DialogueClients.ReloadingFactory reloading(Refreshable<ServicesConfigBlock> scb) {
        return new ReloadingClientFactory(params.withScb(scb), cache);
    }

    @Override
    public <T> T getNonReloading(Class<T> clazz, ServiceConfiguration serviceConf) {
        return new DefaultFactory(ImmutableParams.builder().from(params).build(), cache)
                .getNonReloading(clazz, serviceConf);
    }

    @Override
    public DialogueClients.ReloadingFactory withUserAgent(UserAgent userAgent) {
        return new ReloadingClientFactory(params.withUserAgent(userAgent), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withMaxNumRetries(int value) {
        return new ReloadingClientFactory(params.withMaxNumRetries(value), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withRuntime(ConjureRuntime runtime) {
        return new ReloadingClientFactory(params.withRuntime(runtime), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withRetryExecutor(ScheduledExecutorService executor) {
        return new ReloadingClientFactory(params.withRetryExecutor(executor), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withBlockingExecutor(ExecutorService executor) {
        return new ReloadingClientFactory(params.withBlockingExecutor(executor), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withTaggedMetrics(TaggedMetricRegistry metrics) {
        return new ReloadingClientFactory(params.withTaggedMetrics(metrics), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withNodeSelectionStrategy(NodeSelectionStrategy strategy) {
        return new ReloadingClientFactory(params.withNodeSelectionStrategy(strategy), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withClientQoS(ClientConfiguration.ClientQoS value) {
        return new ReloadingClientFactory(params.withClientQoS(value), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withServerQoS(ClientConfiguration.ServerQoS value) {
        return new ReloadingClientFactory(params.withServerQoS(value), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withRetryOnTimeout(ClientConfiguration.RetryOnTimeout value) {
        return new ReloadingClientFactory(params.withRetryOnTimeout(value), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withSecurityProvider(Provider securityProvider) {
        return new ReloadingClientFactory(params.withSecurityProvider(securityProvider), cache);
    }
}
