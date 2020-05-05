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
import com.palantir.conjure.java.clients.ConjureClients.WithClientOptions;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.refreshable.Refreshable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The {@link ReloadingFactory} abstraction allows users to declaratively construct clients for any of the services
 * named in the given {@link ServicesConfigBlock}. Client behaviour can be customized using the fluent methods from
 * {@link WithClientOptions} or {@link WithDialogueOptions}. Users may also choose to create one-off non-live reloading
 * clients.
 *
 * For maximum performance, we take great care to re-use underlying Apache connection pools as this avoids unnecessary
 * TLS handshakes. To achieve this, it is recommended to call {@link ReloadingFactory#create} once for the lifetime of
 * a JVM. There is no need to manually close clients or connection pools, this will be done automatically.
 *
 * Libraries may depend on interfaces contained in this class and/or interfaces from conjure-java-runtime's
 * {@link ConjureClients}. All other classes in this package are considered package-private implementation details
 * and are subject to change.
 */
public final class DialogueClients {

    public static ReloadingFactory create(Refreshable<ServicesConfigBlock> scb) {
        return new ReloadingClientFactory(
                ImmutableReloadingParams.builder().scb(scb).build(), ChannelCache.createEmptyCache());
    }

    /** Parameters necessary for {@link DialogueChannel#builder()} and constructing an actual BlockingFoo instance. */
    @CheckReturnValue
    public interface WithDialogueOptions<T> {
        T withRuntime(ConjureRuntime runtime);

        /** Exponential backoffs are scheduled on this executor. If this is omitted a singleton will be used. */
        T withRetryExecutor(ScheduledExecutorService retryExecutor);

        /**
         * The Apache http client uses blocking socket operations, so threads from this executor will be used to wait for
         * responses. It's strongly recommended that custom executors support tracing-java. Cached executors are the best
         * fit because we use concurrency limiters to bound concurrent requests.
         */
        T withBlockingExecutor(ExecutorService blockingExecutor);
    }

    /** Low-level API. Most users won't need this, but it is necessary to construct feign-shim clients. */
    public interface ReloadingChannelFactory {
        Channel getChannel(String serviceName);
    }

    public interface ReloadingFactory
            extends ConjureClients.ReloadingClientFactory,
                    WithClientOptions<ReloadingFactory>,
                    WithDialogueOptions<ReloadingFactory>,
                    ConjureClients.NonReloadingClientFactory,
                    ConjureClients.ToReloadingFactory<ReloadingFactory>,
                    ReloadingChannelFactory {}

    private DialogueClients() {}
}
