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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import java.util.function.Supplier;

public final class DialogueChannelFactory {
    private final Supplier<? extends ServicesConfigBlock> conf;
    private final ChannelFactory channelFactory;

    public DialogueChannelFactory(Supplier<? extends ServicesConfigBlock> conf, ChannelFactory channelFactory) {
        this.conf = conf;
        this.channelFactory = channelFactory;
    }

    public Channel create(String service) {
        Supplier<Channel> channelSupplier = new MemoizingComposingSupplier<>(conf, c -> createChannel(service, c));
        return (endpoint, request) -> channelSupplier.get().execute(endpoint, request);
    }

    private Channel createChannel(String service, ServicesConfigBlock services) {
        ServiceConfigurationFactory factory = ServiceConfigurationFactory.of(services);
        return factory.isEnabled(service)
                ? channelFactory.create(ClientConfigurations.of(factory.get(service)))
                : new AlwaysThrowingChannel(service);
    }

    private static final class AlwaysThrowingChannel implements Channel {
        private final String serviceName;

        private AlwaysThrowingChannel(String serviceName) {
            this.serviceName = serviceName;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            return Futures.immediateFailedFuture(
                    new SafeIllegalStateException("Service not configured", SafeArg.of("serviceName", serviceName)));
        }
    }

    public interface ChannelFactory {
        Channel create(ClientConfiguration conf);
    }
}
