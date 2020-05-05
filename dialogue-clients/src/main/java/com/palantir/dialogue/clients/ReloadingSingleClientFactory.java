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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.refreshable.Refreshable;
import java.util.Optional;
import java.util.function.Supplier;
import org.immutables.value.Value;

final class ReloadingSingleClientFactory {
    private final ImmutableSingleClientParams params;
    private final ChannelCache cache;

    ReloadingSingleClientFactory(ImmutableSingleClientParams params, ChannelCache cache) {
        this.params = params;
        this.cache = cache;
    }

    @Value.Immutable
    interface SingleClientParams extends BaseParams {
        String serviceName();

        Refreshable<Optional<ServiceConfiguration>> serviceConf();
    }

    <T> T get(Class<T> serviceClass) {
        Preconditions.checkNotNull(serviceClass, "serviceClass");

        LiveReloadingChannel channel = getChannel();

        return Reflection.callStaticFactoryMethod(serviceClass, channel, params.runtime());
    }

    LiveReloadingChannel getChannel() {
        String channelName = ChannelNames.reloading(params);

        Refreshable<Channel> mapped = params.serviceConf().map(serviceConf -> {
            Preconditions.checkNotNull(serviceConf, "Refreshable must not provide a null serviceConf");
            SafeArg<String> safeArg = SafeArg.of("service", params.serviceName());

            if (!serviceConf.isPresent()) {
                return new AlwaysThrowing(() -> new SafeIllegalStateException("No service conf", safeArg));
            }

            if (serviceConf.get().uris().isEmpty()) {
                return new AlwaysThrowing(() -> new SafeIllegalStateException("No uris", safeArg));
            }

            return cache.getNonReloadingChannel(
                    serviceConf.get(), params, params.retryExecutor(), params.blockingExecutor(), channelName);
        });
        // TODO(dfox): reloading currently forgets which channel we were pinned to. Can we do this in a non-gross way?

        return new LiveReloadingChannel(mapped);
    }

    static final class AlwaysThrowing implements Channel {
        private final Supplier<? extends Throwable> exceptionSupplier;

        AlwaysThrowing(Supplier<? extends Throwable> exceptionSupplier) {
            this.exceptionSupplier = exceptionSupplier;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
            return Futures.immediateFailedFuture(exceptionSupplier.get());
        }

        @Override
        public String toString() {
            return "AlwaysThrowing{exceptionSupplier=" + exceptionSupplier + '}';
        }
    }

    static final class LiveReloadingChannel implements Channel {
        private final Supplier<Channel> supplier;

        LiveReloadingChannel(Supplier<Channel> supplier) {
            this.supplier = supplier;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            Channel delegate = supplier.get();
            return delegate.execute(endpoint, request);
        }

        @Override
        public String toString() {
            return "LiveReloadingChannel{" + supplier.get() + '}';
        }
    }
}
