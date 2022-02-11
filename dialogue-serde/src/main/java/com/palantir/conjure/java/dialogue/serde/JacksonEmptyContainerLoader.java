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

package com.palantir.conjure.java.dialogue.serde;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

final class JacksonEmptyContainerLoader implements EmptyContainerDeserializer {
    private static final SafeLogger log = SafeLoggerFactory.get(JacksonEmptyContainerLoader.class);
    private final ObjectMapper mapper;

    JacksonEmptyContainerLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> tryGetEmptyInstance(TypeMarker<T> token) {
        return (Optional<T>) constructEmptyInstance(token.getType(), token, 10);
    }

    private Optional<Object> constructEmptyInstance(Type type, TypeMarker<?> originalType, int maxRecursion) {
        // handle Map, List, Set
        Optional<Object> collection = coerceCollections(type);
        if (collection.isPresent()) {
            return collection;
        }

        // this is our preferred way to construct instances
        Optional<Object> jacksonInstance = jacksonDeserializeFromNull(type);
        if (jacksonInstance.isPresent()) {
            return jacksonInstance;
        }

        // fallback to manual reflection to handle aliases of optionals (and aliases of aliases of optionals)
        Method method = getJsonCreatorStaticMethod(type);
        if (method != null) {
            Type parameterType = method.getParameters()[0].getParameterizedType();
            // Class<?> parameterType = method.getParameters()[0].getType();
            Optional<Object> parameter =
                    constructEmptyInstance(parameterType, originalType, decrement(maxRecursion, originalType));

            if (parameter.isPresent()) {
                return invokeStaticFactoryMethod(method, parameter.get());
            } else {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Found a @JsonCreator, but couldn't construct the parameter",
                            SafeArg.of("type", type),
                            SafeArg.of("parameter", parameter));
                }
                return Optional.empty();
            }
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Jackson couldn't instantiate an empty instance and also couldn't find a usable @JsonCreator",
                    SafeArg.of("type", type));
        }
        return Optional.empty();
    }

    private static int decrement(int maxRecursion, TypeMarker<?> originalType) {
        if (maxRecursion <= 0) {
            throw new SafeIllegalStateException(
                    "Unable to construct an empty instance as @JsonCreator requires too much recursion",
                    SafeArg.of("type", originalType));
        }
        return maxRecursion - 1;
    }

    private static Optional<Object> coerceCollections(Type type) {
        if (type instanceof Class) {
            return coerceCollections((Class<?>) type);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type raw = parameterizedType.getRawType();
            if (raw instanceof Class) {
                return coerceCollections((Class<?>) raw);
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> coerceCollections(Class<?> clazz) {
        if (List.class.isAssignableFrom(clazz)) {
            return Optional.of(Collections.emptyList());
        } else if (Set.class.isAssignableFrom(clazz)) {
            return Optional.of(Collections.emptySet());
        } else if (Map.class.isAssignableFrom(clazz)) {
            return Optional.of(Collections.emptyMap());
        } else {
            return Optional.empty();
        }
    }

    private Optional<Object> jacksonDeserializeFromNull(Type type) {
        try {
            return Optional.ofNullable(mapper.readerFor(mapper.getTypeFactory().constructType(type))
                    .readValue(mapper.nullNode()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // doesn't attempt to handle multiple @JsonCreator methods on one class
    @Nullable
    private static Method getJsonCreatorStaticMethod(Type type) {
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            for (Method method : clazz.getMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 1
                        && method.getAnnotation(JsonCreator.class) != null) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Optional<Object> invokeStaticFactoryMethod(Method method, Object parameter) {
        try {
            return Optional.ofNullable(method.invoke(null, parameter));
        } catch (IllegalAccessException | InvocationTargetException e) {
            if (log.isDebugEnabled()) {
                log.debug("Reflection instantiation failed", e);
            }
            return Optional.empty();
        }
    }
}
