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

import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.DialogueService;
import com.palantir.dialogue.DialogueServiceFactory;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

final class Reflection {
    private static final SafeLogger log = SafeLoggerFactory.get(Reflection.class);

    private Reflection() {}

    static <T> T callStaticFactoryMethod(Class<T> dialogueInterface, Channel channel, ConjureRuntime conjureRuntime) {
        Preconditions.checkNotNull(dialogueInterface, "dialogueInterface");
        Preconditions.checkNotNull(channel, "channel");

        DialogueService annotation = dialogueInterface.getAnnotation(DialogueService.class);
        if (annotation != null) {
            return createFromAnnotation(dialogueInterface, annotation, channel, conjureRuntime);
        }

        try {
            Optional<Method> legacyMethod = getLegacyStaticOfMethod(dialogueInterface);
            if (legacyMethod.isPresent()) {
                return dialogueInterface.cast(legacyMethod.get().invoke(null, channel, conjureRuntime));
            }
            Method method = getStaticOfMethod(dialogueInterface)
                    .orElseThrow(() -> new SafeIllegalStateException(
                            "A static 'of(Channel, ConjureRuntime)' method is required",
                            SafeArg.of("dialogueInterface", dialogueInterface)));

            return dialogueInterface.cast(method.invoke(null, endpointChannelFactory(channel), conjureRuntime));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new SafeIllegalArgumentException(
                    "Failed to reflectively construct dialogue client. Please check the "
                            + "dialogue interface class has a public static of(Channel, ConjureRuntime) method",
                    e,
                    SafeArg.of("dialogueInterface", dialogueInterface));
        }
    }

    private static Optional<Method> getLegacyStaticOfMethod(Class<?> dialogueInterface) {
        try {
            return Optional.of(dialogueInterface.getMethod("of", Channel.class, ConjureRuntime.class));
        } catch (NoSuchMethodException e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to get static 'of' method", SafeArg.of("interface", dialogueInterface), e);
            }
            return Optional.empty();
        }
    }

    private static Optional<Method> getStaticOfMethod(Class<?> dialogueInterface) {
        try {
            return Optional.of(dialogueInterface.getMethod("of", EndpointChannelFactory.class, ConjureRuntime.class));
        } catch (NoSuchMethodException e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to get static 'of' method", SafeArg.of("interface", dialogueInterface), e);
            }
            return Optional.empty();
        }
    }

    private static <T> T createFromAnnotation(
            Class<T> dialogueInterface,
            DialogueService dialogueService,
            Channel channel,
            ConjureRuntime conjureRuntime) {
        Class<? extends DialogueServiceFactory<?>> serviceFactoryClass = dialogueService.value();
        EndpointChannelFactory factory = endpointChannelFactory(channel);
        Object client;
        try {
            client = serviceFactoryClass.getConstructor().newInstance().create(factory, conjureRuntime);
        } catch (NoSuchMethodException e) {
            throw new SafeIllegalArgumentException(
                    "Failed to reflectively construct dialogue client. The service factory class must have a "
                            + "public no-arg constructor.",
                    e,
                    SafeArg.of("dialogueInterface", dialogueInterface),
                    SafeArg.of("serviceFactoryClass", serviceFactoryClass));
        } catch (ReflectiveOperationException e) {
            throw new SafeIllegalArgumentException(
                    "Failed to reflectively construct dialogue client.",
                    e,
                    SafeArg.of("dialogueInterface", dialogueInterface),
                    SafeArg.of("serviceFactoryClass", serviceFactoryClass));
        }
        if (dialogueInterface.isInstance(client)) {
            return dialogueInterface.cast(client);
        }
        throw new SafeIllegalArgumentException(
                "Dialogue service factory produced an incompatible service",
                SafeArg.of("dialogueInterface", dialogueInterface),
                SafeArg.of("serviceFactoryClass", serviceFactoryClass),
                SafeArg.of("invalidClientType", client.getClass()),
                SafeArg.of("invalidClient", client));
    }

    private static EndpointChannelFactory endpointChannelFactory(Channel channel) {
        if (channel instanceof EndpointChannelFactory) {
            return (EndpointChannelFactory) channel;
        }
        return endpoint -> request -> channel.execute(endpoint, request);
    }
}
