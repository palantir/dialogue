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
import com.palantir.dialogue.ConstructUsing;
import com.palantir.dialogue.Factory;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Collectors;

public final class ClientPoolImpl implements ClientPool {

    private final SharedResources sharedResources = null;

    @Override
    public <T> T get(Class<T> dialogueInterface, Listenable<ClientConfig> config) {
        Channel channel = smartChannel(config);
        return instantiateDialogueInterface(dialogueInterface, channel);
    }

    @Override
    public Channel smartChannel(Listenable<ClientConfig> config) {
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
    public Channel rawHttpChannel(String uri, Listenable<ClientConfig> config) {
        // TODO(dfox): allow people to live-reload the entire client type!
        Class<? extends HttpChannelFactory> httpChannelFactory = config.getListenableCurrentValue().httpChannelFactory;

        HttpChannelFactory channelFactory = invokeZeroArgConstructor(httpChannelFactory);

        return channelFactory.construct(uri, config, sharedResources);
    }

    @VisibleForTesting
    static <T> T instantiateDialogueInterface(Class<T> dialogueInterface, Channel smartChannel) {
        ConstructUsing annotation = dialogueInterface.getDeclaredAnnotation(ConstructUsing.class);
        Preconditions.checkNotNull(
                annotation,
                "@DialogueInterface annotation must be present on interface",
                SafeArg.of("interface", dialogueInterface.getName()));

        Class<?> factoryClass = annotation.value();
        // this is safe because the annotation constrains the value
        Factory<T> factory = (Factory<T>) invokeZeroArgConstructor(factoryClass);
        return factory.construct(smartChannel);
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
    public void close() {}
}
