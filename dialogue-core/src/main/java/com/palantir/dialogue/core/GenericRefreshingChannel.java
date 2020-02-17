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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.Optional;
import java.util.function.Supplier;

final class GenericRefreshingChannel implements Channel {
    private final Supplier<Channel> channelSupplier;

    private GenericRefreshingChannel(Supplier<Channel> channelSupplier) {
        this.channelSupplier = channelSupplier;
    }

    /**
     * Returns a refreshing {@link Channel} for a service identified by {@code service} in
     * {@link ServicesConfigBlock#services()}.
     */
    public static <T> Channel create(Supplier<T> confSupplier, ChannelFactory<T> channelFactory) {
        Supplier<Channel> channelSupplier = new MemoizingComposingSupplier<>(confSupplier, conf -> {
            return channelFactory.create(conf).orElse(AlwaysThrowingChannel.INSTANCE);
        });
        return new GenericRefreshingChannel(channelSupplier);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return channelSupplier.get().execute(endpoint, request);
    }

    private enum AlwaysThrowingChannel implements Channel {
        INSTANCE;

        @Override
        public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
            return Futures.immediateFailedFuture(new SafeIllegalStateException("Service not configured"));
        }
    }

    public interface ChannelFactory<T> {
        Optional<Channel> create(T conf);
    }
}
