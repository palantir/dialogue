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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.ConstructUsing;
import com.palantir.dialogue.Factory;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientPoolImpl implements ClientPool {
    private static final Logger log = LoggerFactory.getLogger(ClientPoolImpl.class);
    private static AtomicInteger instances = new AtomicInteger(0);

    private final SharedResources sharedResources = new SharedResourcesImpl();
    private final ConjureRuntime runtime;

    @SuppressWarnings("Slf4jLogsafeArgs")
    ClientPoolImpl(ConjureRuntime runtime) {
        this.runtime = runtime;

        int instanceNumber = instances.incrementAndGet();
        if (instanceNumber > 1) {
            log.warn("Constructing ClientPool number {}, try to re-use the existing instance", instanceNumber);
        }
    }

    @Override
    public <T> T get(Class<T> dialogueInterface, Listenable<DialogueConfig> config) {
        Channel channel = smartChannel(config);
        // Note: if you call this many times, you'll get entirely independent 'smart channel' instances, which means
        // they each have their own idea about blacklisting / concurrency limiting etc.
        return conjure(dialogueInterface, channel, runtime);
    }

    @Override
    public Channel smartChannel(Listenable<DialogueConfig> config) {
        // This is a naive approach to live reloading, as it throws away all kinds of useful state (e.g. active request
        // count, blacklisting info etc).
        return RefreshingChannelFactory.RefreshingChannel.create(config::getListenableCurrentValue, conf -> {
            List<Channel> channels = conf.uris().stream()
                    .map(uri -> {
                        // important that this re-uses resources under the hood, as it gets called often!
                        return rawHttpChannel(uri, config);
                    })
                    .collect(Collectors.toList());

            return Channels.create(channels, conf.userAgent, conf.legacyClientConfiguration);
        });
    }

    @Override
    public Channel rawHttpChannel(String uri, Listenable<DialogueConfig> config) {
        Class<? extends HttpChannelFactory> factoryClazz = config.getListenableCurrentValue().httpChannelFactory;

        config.subscribe(() -> {
            if (config.getListenableCurrentValue().httpChannelFactory != factoryClazz) {
                log.warn("Live-reloading the *type* of underlying http channel is not currently supported");
            }
        });

        HttpChannelFactory channelFactory = getOnlyEnumConstant(factoryClazz);

        // by passing in sharedResources, we're enable to avoid re-creating the expensive underlying clients
        return channelFactory.construct(uri, config, sharedResources);
    }

    /** Just give me a working implementation of the provided conjure-generated interface. */
    @VisibleForTesting
    static <T> T conjure(Class<T> dialogueInterface, Channel smartChannel, ConjureRuntime runtime) {
        ConstructUsing annotation = dialogueInterface.getDeclaredAnnotation(ConstructUsing.class);
        Preconditions.checkNotNull(
                annotation,
                "@ConstructUsing annotation must be present on interface",
                SafeArg.of("interface", dialogueInterface.getName()));

        Class<?> factoryClass = annotation.value();
        // this is safe because of the ConstructUsing annotation's type parameters
        Factory<T> factory = (Factory<T>) getOnlyEnumConstant(factoryClass);
        return factory.construct(smartChannel, runtime);
    }

    private static <F> F getOnlyEnumConstant(Class<F> factoryClass) {
        Preconditions.checkState(factoryClass.isEnum(), "Factory must be an enum");
        Preconditions.checkState(factoryClass.getEnumConstants().length == 1, "Enum must have 1 value");
        return factoryClass.getEnumConstants()[0];
    }

    @Override
    public void close() throws IOException {
        sharedResources.close();
    }
}
