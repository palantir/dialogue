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
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
        return instantiateDialogueInterface(dialogueInterface, channel, runtime);
    }

    @Override
    public Channel smartChannel(Listenable<DialogueConfig> config) {
        // This is a naive live reloading approach, as it throws away all kinds of useful state (active
        // request count, blacklisting info etc).
        return RefreshingChannelFactory.RefreshingChannel.create(config::getListenableCurrentValue, conf -> {
            List<Channel> channels = conf.uris().stream()
                    .map(uri -> {
                        // important that this re-uses resources under the hood, as we'll be calling it often!
                        return rawHttpChannel(uri, config);
                    })
                    .collect(Collectors.toList());

            return Channels.create(channels, conf.userAgent, conf.legacyClientConfiguration);
        });
    }

    @Override
    public Channel rawHttpChannel(String uri, Listenable<DialogueConfig> config) {
        // TODO(dfox): allow people to live-reload the entire client type!
        Class<? extends HttpChannelFactory> httpChannelFactory = config.getListenableCurrentValue().httpChannelFactory;

        HttpChannelFactory channelFactory = invokeZeroArgConstructor(httpChannelFactory);

        return channelFactory.construct(uri, config, sharedResources);
    }

    @VisibleForTesting
    static <T> T instantiateDialogueInterface(
            Class<T> dialogueInterface, Channel smartChannel, ConjureRuntime runtime) {
        ConstructUsing annotation = dialogueInterface.getDeclaredAnnotation(ConstructUsing.class);
        Preconditions.checkNotNull(
                annotation,
                "@DialogueInterface annotation must be present on interface",
                SafeArg.of("interface", dialogueInterface.getName()));

        Class<?> factoryClass = annotation.value();
        // this is safe because the annotation constrains the value
        Factory<T> factory = (Factory<T>) invokeZeroArgConstructor(factoryClass);
        return factory.construct(smartChannel, runtime);
    }

    private static <F> F invokeZeroArgConstructor(Class<F> factoryClass) {
        try {
            Constructor<?> constructor = factoryClass.getDeclaredConstructors()[0];
            Preconditions.checkState(constructor.getParameterCount() == 0, "Constructor must be 0 arg");
            return (F) constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new SafeRuntimeException(
                    "Failed to reflectively instantiate", SafeArg.of("interface", factoryClass.getName()));
        }
    }

    @Override
    public void close() throws IOException {
        sharedResources.close();
    }
}
