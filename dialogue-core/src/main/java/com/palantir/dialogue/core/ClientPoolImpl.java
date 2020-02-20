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
import com.google.common.collect.Lists;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.DialogueFactory;
import com.palantir.dialogue.Factory;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public final class ClientPoolImpl implements ClientPool {

    @Override
    public <T> T get(Class<T> dialogueInterface, Listenable<ClientConfig> config) {
        Channel channel = smartChannel(config);
        return instantiate(dialogueInterface, channel);
    }

    @Override
    public Channel smartChannel(Listenable<ClientConfig> config) {
        ClientConfig clientConfig = config.get(); // TODO(dfox): live reloading!

        List<String> uris = clientConfig.uris();
        List<Channel> channels = Lists.transform(uris, uri -> rawHttpChannel(uri, config));

        return Channels.create(channels, clientConfig.userAgent, clientConfig.legacyClientConfiguration);
    }

    @Override
    public Channel rawHttpChannel(String _uri, Listenable<ClientConfig> config) {
        ClientConfig clientConfig = config.get(); // TODO(dfox): live reloading!

        // TODO(dfox): jokes we can't directly compile against any of the impls as this would be circular... SERVICELOAD
        switch (clientConfig.httpClientType) {
            case APACHE:
                // ApacheHttpClientChannels.create(clientConfig)
                break;
            case OKHTTP:
                break;
            case HTTP_URL_CONNECTION:
                break;
            case JAVA9_HTTPCLIENT:
                break;
        }

        throw new SafeIllegalArgumentException(
                "Unable to construct a raw channel", SafeArg.of("type", clientConfig.httpClientType));
    }

    @VisibleForTesting
    static <T> T instantiate(Class<T> dialogueInterface, Channel smartChannel) {
        DialogueFactory annotation = dialogueInterface.getDeclaredAnnotation(DialogueFactory.class);
        Preconditions.checkNotNull(
                annotation,
                "@DialogueInterface annotation must be present on interface",
                SafeArg.of("interface", dialogueInterface.getName()));

        Class<? extends Factory<?>> factoryClass = annotation.value();
        try {
            Constructor<?> constructor = factoryClass.getDeclaredConstructors()[0];
            Preconditions.checkState(constructor.getParameterCount() == 0, "Constructor must be 0 arg");
            // this is safe because the annotation constrains the value
            Factory<T> factory = (Factory<T>) constructor.newInstance();
            return factory.construct(smartChannel);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new SafeRuntimeException(
                    "Failed to reflectively instantiate", SafeArg.of("interface", dialogueInterface.getName()));
        }
    }

    @Override
    public void close() {}
}
