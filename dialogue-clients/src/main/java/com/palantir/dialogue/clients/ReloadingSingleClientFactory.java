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
import com.palantir.dialogue.Channel;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.refreshable.Refreshable;
import java.util.Optional;
import org.immutables.value.Value;

final class ReloadingSingleClientFactory implements DialogueClients.SingleReloadingFactory {

    private final ImmutableParams3 params;
    private final ChannelCache cache;

    ReloadingSingleClientFactory(ImmutableParams3 params, ChannelCache cache) {
        this.params = params;
        this.cache = cache;
    }

    @Value.Immutable
    interface Params3 extends BaseParams {
        Optional<String> serviceName();

        Refreshable<Optional<ServiceConfiguration>> serviceConf();
    }

    @Override
    public <T> T get(Class<T> serviceClass) {
        Preconditions.checkNotNull(serviceClass, "serviceClass");

        Refreshable<Channel> mapped = params.serviceConf().map(serviceConf -> {
            Preconditions.checkNotNull(serviceConf, "Refreshable must not provide a null serviceConf");
            String channelName = "dialogue-reloading-" + params.serviceName().orElseGet(serviceClass::getSimpleName);

            if (!serviceConf.isPresent()) {
                return new AlwaysThrowing(
                        () -> new SafeIllegalStateException("No service conf", SafeArg.of("channelName", channelName)));
            }

            if (serviceConf.get().uris().isEmpty()) {
                return new AlwaysThrowing(
                        () -> new SafeIllegalStateException("No uris", SafeArg.of("channelName", channelName)));
            }

            return cache.getNonReloadingChannel(
                    serviceConf.get(), params, params.retryExecutor(), params.blockingExecutor(), channelName);
        });
        // TODO(dfox): reloading currently forgets which channel we were pinned to. Can we do this in a non-gross way?

        LiveReloadingChannel reloadingChannel = new LiveReloadingChannel(mapped);
        return Reflection.callStaticFactoryMethod(serviceClass, reloadingChannel, params.runtime());
    }

    @Override
    public DialogueClients.SingleReloadingFactory withServiceName(String serviceName) {
        return new ReloadingSingleClientFactory(params.withServiceName(serviceName), cache);
    }
}
