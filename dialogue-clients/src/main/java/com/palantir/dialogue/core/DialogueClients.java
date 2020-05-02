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

import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Only this class is public API, everything else is package private and may change.
 *
 * All of the interfaces have been split up into separate chunks so that libraries can depend on only as much as they
 * need. This also makes it more convenient to implement these interfaces elsewhere (e.g. in WC).
 */
public final class DialogueClients {

    public static Factory create() {
        return DefaultFactory.create();
    }

    public interface ReloadingClients {
        <T> T get(Class<T> serviceClass, String serviceName);
    }

    public interface SingleClientFactory {
        <T> T get(Class<T> serviceClass);
    }

    public interface NonReloadingClients {
        <T> T getNonReloading(Class<T> clazz, ServiceConfiguration serviceConf);
    }

    /**
     * Allows users to tweak values of {@link AugmentClientConfig} and {@link BaseParams}. Forces us to expose the same
     * methods on {@link ReloadingClientFactory} and {@link DefaultFactory}.
     */
    @CheckReturnValue
    public interface WithMethodsMixin<T> {
        T withRuntime(ConjureRuntime runtime);

        T withRetryExecutor(ScheduledExecutorService executor);

        T withBlockingExecutor(ExecutorService executor);

        T withTaggedMetrics(TaggedMetricRegistry metrics);

        T withUserAgent(UserAgent agent);

        T withNodeSelectionStrategy(NodeSelectionStrategy strategy);

        T withClientQoS(ClientConfiguration.ClientQoS value);

        T withServerQoS(ClientConfiguration.ServerQoS value);

        T withRetryOnTimeout(ClientConfiguration.RetryOnTimeout value);

        T withSecurityProvider(java.security.Provider securityProvider);

        T withMaxNumRetries(int maxNumRetries);
    }

    public interface WithServiceName<T> {
        @CheckReturnValue
        T withServiceName(String serviceName);
    }

    public interface ToReloadingFactory<U> {
        @CheckReturnValue
        U reloading(Refreshable<ServicesConfigBlock> scb);
    }

    public interface ToSingleReloadingFactory<U> {
        @CheckReturnValue
        U reloadingServiceConfiguration(Refreshable<ServiceConfiguration> refreshable);
    }

    public interface Factory
            extends NonReloadingClients,
                    WithMethodsMixin<Factory>,
                    ToReloadingFactory<ReloadingFactory>,
                    ToSingleReloadingFactory<SingleReloadingFactory> {}

    public interface ReloadingFactory
            extends ReloadingClients,
                    NonReloadingClients,
                    WithMethodsMixin<ReloadingFactory>,
                    ToReloadingFactory<ReloadingFactory> {}

    public interface SingleReloadingFactory extends SingleClientFactory, WithServiceName<SingleReloadingFactory> {}

    private DialogueClients() {}
}
