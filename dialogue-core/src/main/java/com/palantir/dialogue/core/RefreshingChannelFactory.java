/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfigurationFactory;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility that creates {@link Channel}s that will be automatically recreated any time the given
 * {@link ServicesConfigBlock} changes.
 */
public final class RefreshingChannelFactory {
    private final Supplier<? extends ServicesConfigBlock> conf;
    private final ChannelFactory channelFactory;

    public RefreshingChannelFactory(Supplier<? extends ServicesConfigBlock> conf, ChannelFactory channelFactory) {
        this.conf = conf;
        this.channelFactory = channelFactory;
    }

    public interface ChannelFactory {
        Channel create(ClientConfiguration conf);
    }

    /**
     * Returns a refreshing {@link Channel} for a service identified by {@code service} in
     * {@link ServicesConfigBlock#services()}.
     */
    public Channel create(String service) {
        return RefreshingChannel.create(conf, servicesConfigBlock -> {
            ServiceConfigurationFactory factory = ServiceConfigurationFactory.of(servicesConfigBlock);
            if (!factory.isEnabled(service)) {
                return new AlwaysThrowingChannel(service);
            }

            ServiceConfiguration serviceConfiguration = factory.get(service);
            ClientConfiguration clientConfiguration = ClientConfigurations.of(serviceConfiguration);
            Channel channel = channelFactory.create(clientConfiguration);
            return channel;
        });
    }

    private static final class AlwaysThrowingChannel implements Channel {
        private final String serviceName;

        private AlwaysThrowingChannel(String serviceName) {
            this.serviceName = serviceName;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
            return Futures.immediateFailedFuture(
                    new SafeIllegalStateException("Service not configured", SafeArg.of("serviceName", serviceName)));
        }
    }

    @VisibleForTesting
    static final class RefreshingChannel implements Channel {
        private final Supplier<Channel> channelSupplier;

        private RefreshingChannel(Supplier<Channel> channelSupplier) {
            this.channelSupplier = channelSupplier;
        }

        /**
         * Returns a Channel which will be built by applying the {@code channelFactory} to whatever the latest config
         * provided by the {@code confSupplier} is. Avoids invoking the channelFactory if the config hasn't changed.
         */
        static <T> Channel create(Supplier<T> confSupplier, Function<T, Channel> channelFactory) {
            // Beware: the memoizing composing supplier does updates in a synchronized block, which can block all
            // other client threads if one call to the channelFactory happens to block (or worse deadlock)!
            Supplier<Channel> channelSupplier = new MemoizingComposingSupplier<>(confSupplier, channelFactory);
            return new RefreshingChannel(channelSupplier);
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            return channelSupplier.get().execute(endpoint, request);
        }
    }
}
