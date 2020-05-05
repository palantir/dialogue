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
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.clients.ConjureClients;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.refreshable.Refreshable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * For maximum performance, care should be taken when constructing clients to re-use the underlying Apache connection
 * pools, so that
 *
 *
 * The basic client factory mixins are defined in {@link ConjureClients}, this class just defines some
 * additional dialogue-specific chunks.
 *
 * Libraries should depend on only as much as they need.
 *
 * The entire public API is contained in this class and c-j-r's {@link ConjureClients}.
 * All other classes in this package are implementation details (and are package-private anyway).
 */
public final class DialogueClients {

    public static ReloadingFactory create(Refreshable<ServicesConfigBlock> scb) {
        return new ReloadingClientFactory(
                ImmutableReloadingParams.builder().scb(scb).build(), ChannelCache.createEmptyCache());
    }

    /** Low-level API. Most users won't need this, but it is necessary to construct feign-shim clients. */
    public interface ReloadingChannelFactory {
        Channel getChannel(String serviceName);
    }

    @CheckReturnValue
    public interface WithDialogueOptions<T> {
        T withRuntime(ConjureRuntime runtime);

        T withRetryExecutor(ScheduledExecutorService executor);

        T withBlockingExecutor(ExecutorService executor);
    }

    public interface ReloadingFactory
            extends ConjureClients.ReloadingClientFactory,
                    ConjureClients.WithClientOptions<ReloadingFactory>,
                    WithDialogueOptions<ReloadingFactory>,
                    ConjureClients.NonReloadingClientFactory,
                    ConjureClients.ToReloadingFactory<ReloadingFactory>,
                    ReloadingChannelFactory {}

    private DialogueClients() {}
}
