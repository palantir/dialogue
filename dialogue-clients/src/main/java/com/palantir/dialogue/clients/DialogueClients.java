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

import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.clients.ConjureClients;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.refreshable.Refreshable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The basic client factory mixins are defined in {@link ConjureClients}, this class just defines some
 * additional dialogue-specific chunks.
 *
 * Libraries should depend on only as much as they need.
 */
public final class DialogueClients {

    public static Factory create() {
        return DefaultFactory.create();
    }

    public interface SingleClientFactory {
        <T> T get(Class<T> serviceClass);
    }

    @CheckReturnValue
    public interface WithDialogueOptions<T> {
        T withRuntime(ConjureRuntime runtime);

        T withRetryExecutor(ScheduledExecutorService executor);

        T withBlockingExecutor(ExecutorService executor);
    }

    public interface WithServiceName<T> {
        @CheckReturnValue
        T withServiceName(String serviceName);
    }

    public interface ToSingleReloadingFactory<U> {
        @CheckReturnValue
        U reloadingServiceConfiguration(Refreshable<ServiceConfiguration> refreshable);
    }

    public interface Factory
            extends ConjureClients.NonReloadingClientFactory,
                    ConjureClients.WithClientOptions<Factory>,
                    WithDialogueOptions<Factory>,
                    ConjureClients.ToReloadingFactory<ReloadingFactory>,
                    ToSingleReloadingFactory<SingleReloadingFactory> {}

    public interface ReloadingFactory
            extends ConjureClients.ReloadingClientFactory,
                    ConjureClients.NonReloadingClientFactory,
                    ConjureClients.WithClientOptions<ReloadingFactory>,
                    WithDialogueOptions<ReloadingFactory>,
                    ConjureClients.ToReloadingFactory<ReloadingFactory>,
                    WithServiceName<SingleReloadingFactory> {}

    public interface SingleReloadingFactory
            extends SingleClientFactory,
                    WithServiceName<SingleReloadingFactory>,
                    ConjureClients.WithClientOptions<SingleReloadingFactory>,
                    WithDialogueOptions<SingleReloadingFactory> {}

    private DialogueClients() {}
}
