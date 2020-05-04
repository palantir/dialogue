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

import com.google.errorprone.annotations.Immutable;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.security.Provider;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.immutables.value.Value;

/**
 * Guiding principle: Users can't be trusted to close things to prevent OOMs, we must do it automatically for them.
 * It should be impossible to leak threads or memory by calling methods of this class.
 */
final class DefaultFactory implements DialogueClients.Factory {
    private final ImmutableParams params;
    private final ChannelCache cache;

    DefaultFactory(ImmutableParams params, ChannelCache cache) {
        this.params = params;
        this.cache = cache;
    }

    static DefaultFactory create() {
        return new DefaultFactory(ImmutableParams.builder().build(), new ChannelCache());
    }

    @Immutable
    @Value.Style(passAnnotations = Immutable.class)
    @Value.Immutable
    interface Params extends BaseParams, AugmentClientConfig {}

    /** This uses the same ChannelCache, so the minimum number of apache clients will be created. */
    @Override
    public DialogueClients.ReloadingFactory reloading(Refreshable<ServicesConfigBlock> scb) {
        return new ReloadingClientFactory(
                ImmutableReloadingParams.builder().from(params).scb(scb).build(), cache);
    }

    @Override
    public DialogueClients.SingleReloadingFactory reloadingServiceConfiguration(
            Refreshable<ServiceConfiguration> refreshable) {
        return new ReloadingSingleClientFactory(
                ImmutableParams3.builder()
                        .from(params)
                        .serviceConf(refreshable.map(Optional::ofNullable))
                        .build(),
                cache);
    }

    @Override
    public <T> T getNonReloading(Class<T> clazz, ServiceConfiguration serviceConf) {
        Channel channel = cache.getNonReloadingChannel(
                serviceConf,
                params,
                params.retryExecutor(),
                params.blockingExecutor(),
                "dialogue-nonreloading-" + clazz.getSimpleName());

        return Reflection.callStaticFactoryMethod(clazz, channel, params.runtime());
    }

    @Override
    public DialogueClients.Factory withRetryExecutor(ScheduledExecutorService executor) {
        return new DefaultFactory(params.withRetryExecutor(executor), cache);
    }

    @Override
    public DialogueClients.Factory withBlockingExecutor(ExecutorService executor) {
        return new DefaultFactory(params.withBlockingExecutor(executor), cache);
    }

    @Override
    public DialogueClients.Factory withRuntime(ConjureRuntime runtime) {
        return new DefaultFactory(params.withRuntime(runtime), cache);
    }

    @Override
    public DialogueClients.Factory withUserAgent(UserAgent agent) {
        return new DefaultFactory(params.withUserAgent(agent), cache);
    }

    @Override
    public DialogueClients.Factory withNodeSelectionStrategy(NodeSelectionStrategy strategy) {
        return new DefaultFactory(params.withNodeSelectionStrategy(strategy), cache);
    }

    @Override
    public DialogueClients.Factory withClientQoS(ClientConfiguration.ClientQoS value) {
        return new DefaultFactory(params.withClientQoS(value), cache);
    }

    @Override
    public DialogueClients.Factory withServerQoS(ClientConfiguration.ServerQoS value) {
        return new DefaultFactory(params.withServerQoS(value), cache);
    }

    @Override
    public DialogueClients.Factory withRetryOnTimeout(ClientConfiguration.RetryOnTimeout value) {
        return new DefaultFactory(params.withRetryOnTimeout(value), cache);
    }

    @Override
    public DialogueClients.Factory withSecurityProvider(Provider securityProvider) {
        return new DefaultFactory(params.withSecurityProvider(securityProvider), cache);
    }

    @Override
    public DialogueClients.Factory withMaxNumRetries(int maxNumRetries) {
        return new DefaultFactory(params.withMaxNumRetries(maxNumRetries), cache);
    }

    @Override
    public DialogueClients.Factory withTaggedMetrics(TaggedMetricRegistry metrics) {
        return new DefaultFactory(params.withTaggedMetrics(metrics), cache);
    }
}
