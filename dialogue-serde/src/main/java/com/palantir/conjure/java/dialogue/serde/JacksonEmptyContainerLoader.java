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
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JacksonEmptyContainerLoader implements EmptyContainerDeserializer {
    private static final Logger log = LoggerFactory.getLogger(JacksonEmptyContainerLoader.class);
    private final ObjectMapper mapper;

    JacksonEmptyContainerLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Nullable
    @Override
    public <T> T getEmptyInstance(TypeMarker<T> token) {
        Class<?> clazz = getClass(token.getType());
        return (T) constructEmptyInstance(clazz, token, 10).orElse(null);
    }

    private Optional<Object> constructEmptyInstance(Class<?> clazz, TypeMarker<?> token, int maxRecursion) {

        // handle Map, List, Set
        Optional<Object> collection = coerceCollections(clazz);
        if (collection.isPresent()) {
            return collection;
        }

        // this is our preferred way to construct instances
        Optional<Object> jacksonInstance = jacksonDeserializeFromNull(clazz);
        if (jacksonInstance.isPresent()) {
            return jacksonInstance;
        }

        // fallback to manual reflection to handle aliases of optionals (and aliases of aliases of optionals)
        Optional<Method> jsonCreator = getJsonCreatorStaticMethod(clazz);
        if (jsonCreator.isPresent()) {
            Method method = jsonCreator.get();
            Class<?> parameterType = method.getParameters()[0].getType();
            Optional<Object> parameter = constructEmptyInstance(parameterType, token, decrement(maxRecursion, token));

            if (parameter.isPresent()) {
                return invokeStaticFactoryMethod(method, parameter.get());
            } else {
                log.debug(
                        "Found a @JsonCreator, but couldn't construct the parameter",
                        SafeArg.of("type", token),
                        SafeArg.of("parameter", parameter));
                return Optional.empty();
            }
        }

        log.debug(
                "Jackson couldn't instantiate an empty instance and also couldn't find a usable @JsonCreator",
                SafeArg.of("type", token));
        return Optional.empty();
    }

    private static int decrement(int maxRecursion, TypeMarker<?> originalType) {
        Preconditions.checkState(
                maxRecursion > 0,
                "Unable to construct an empty instance as @JsonCreator requires too much recursion",
                SafeArg.of("type", originalType));
        return maxRecursion - 1;
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

    private Optional<Object> jacksonDeserializeFromNull(Class<?> clazz) {
        try {
            return Optional.ofNullable(mapper.readValue("null", clazz));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // doesn't attempt to handle multiple @JsonCreator methods on one class
    private static Optional<Method> getJsonCreatorStaticMethod(@Nonnull Class<?> clazz) {
        return Arrays.stream(clazz.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 1
                        && method.getAnnotation(JsonCreator.class) != null)
                .findFirst();
    }

    private static Optional<Object> invokeStaticFactoryMethod(Method method, Object parameter) {
        try {
            return Optional.ofNullable(method.invoke(null, parameter));
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.debug("Reflection instantiation failed", e);
            return Optional.empty();
        }
    }

    private static Class<?> getClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            return getClass(parameterized.getRawType());
        } else {
            throw new SafeIllegalArgumentException("Unable to getClass", SafeArg.of("type", type));
        }
    }
}
