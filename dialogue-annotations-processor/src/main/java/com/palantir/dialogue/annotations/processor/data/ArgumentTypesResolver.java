/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.annotations.processor.data;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MoreCollectors;
import com.palantir.common.streams.KeyedStream;
import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.RequestBody;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public final class ArgumentTypesResolver {

    @SuppressWarnings("DangerousIdentityKey")
    @VisibleForTesting
    static final ImmutableMap<TypeName, String> PARAMETER_SERIALIZER_TYPES =
            ImmutableMap.copyOf(KeyedStream.stream(new ImmutableMap.Builder<Class<?>, String>()
                            .put(BearerToken.class, "BearerToken")
                            .put(AuthHeader.class, "AuthHeader")
                            .put(Boolean.class, "Boolean")
                            .put(OffsetDateTime.class, "DateTime")
                            .put(Double.class, "Double")
                            .put(Float.class, "Float")
                            .put(Integer.class, "Integer")
                            .put(Long.class, "Long")
                            .put(Character.class, "Char")
                            .put(ResourceIdentifier.class, "Rid")
                            .put(SafeLong.class, "SafeLong")
                            .put(String.class, "String")
                            .put(UUID.class, "Uuid")
                            .buildOrThrow())
                    .mapKeys((Function<Class<?>, ClassName>) ClassName::get)
                    .map(value -> "serialize" + value)
                    .collectToMap());

    private final ResolverContext context;

    public ArgumentTypesResolver(ResolverContext context) {
        this.context = context;
    }

    public ArgumentType getArgumentType(VariableElement param) {
        TypeMirror typeMirror = param.asType();

        return getPrimitiveType(typeMirror)
                .or(() -> getListType(typeMirror))
                .or(() -> getOptionalType(typeMirror))
                .or(() -> getRawRequestBodyType(typeMirror))
                .or(() -> getAliasType(typeMirror))
                .orElseGet(() -> ArgumentTypes.customType(TypeName.get(typeMirror)));
    }

    private Optional<String> getPrimitiveSerializerMethodName(TypeMirror typeMirror) {
        TypeName typeName = TypeName.get(typeMirror);

        return Optional.ofNullable(PARAMETER_SERIALIZER_TYPES.get(typeName.box()));
    }

    private Optional<ArgumentType> getPrimitiveType(TypeMirror typeMirror) {
        TypeName typeName = TypeName.get(typeMirror);

        return getPrimitiveSerializerMethodName(typeMirror)
                .map(methodName -> ArgumentTypes.primitive(typeName, methodName));
    }

    private Optional<ArgumentType> getListType(TypeMirror typeMirror) {
        TypeName typeName = TypeName.get(typeMirror);

        return context.getGenericInnerType(List.class, typeMirror)
                .flatMap(this::getPrimitiveSerializerMethodName)
                .map(methodName -> ArgumentTypes.list(typeName, methodName));
    }

    private Optional<ArgumentType> getOptionalType(TypeMirror typeMirror) {
        TypeName typeName = TypeName.get(typeMirror);

        if (context.isSameTypes(typeMirror, OptionalInt.class)) {
            return Optional.of(ArgumentTypes.optional(
                    typeName,
                    ImmutableOptionalType.builder()
                            .isPresentMethodName("isPresent")
                            .valueGetMethodName("getAsInt")
                            .innerType(getPrimitiveType(context.getTypeMirror(Integer.class))
                                    .get())
                            .build()));
        }

        if (context.isSameTypes(typeMirror, OptionalLong.class)) {
            return Optional.of(ArgumentTypes.optional(
                    typeName,
                    ImmutableOptionalType.builder()
                            .isPresentMethodName("isPresent")
                            .valueGetMethodName("getAsLong")
                            .innerType(getPrimitiveType(context.getTypeMirror(Long.class))
                                    .get())
                            .build()));
        }

        if (context.isSameTypes(typeMirror, OptionalDouble.class)) {
            return Optional.of(ArgumentTypes.optional(
                    typeName,
                    ImmutableOptionalType.builder()
                            .isPresentMethodName("isPresent")
                            .valueGetMethodName("getAsDouble")
                            .innerType(getPrimitiveType(context.getTypeMirror(Double.class))
                                    .get())
                            .build()));
        }

        return context.getGenericInnerType(Optional.class, typeMirror)
                .map(innerType -> getPrimitiveType(innerType)
                        .or(() -> getAliasType(innerType))
                        .orElseGet(() -> getCustomType(typeMirror)))
                .map(innerType -> ArgumentTypes.optional(
                        typeName,
                        ImmutableOptionalType.builder()
                                .isPresentMethodName("isPresent")
                                .valueGetMethodName("get")
                                .innerType(innerType)
                                .build()));
    }

    private Optional<ArgumentType> getRawRequestBodyType(TypeMirror typeMirror) {
        if (!context.isAssignable(typeMirror, RequestBody.class)) {
            return Optional.empty();
        }

        return Optional.of(ArgumentTypes.rawRequestBody(TypeName.get(typeMirror)));
    }

    private Optional<ArgumentType> getAliasType(TypeMirror typeMirror) {
        TypeName typeName = TypeName.get(typeMirror);

        return context.maybeAsDeclaredType(typeMirror).stream()
                .flatMap(declaredType -> declaredType.asElement().getEnclosedElements().stream())
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(element -> element.getSimpleName().contentEquals("get")
                        && element.getModifiers().contains(Modifier.PUBLIC)
                        && !element.getModifiers().contains(Modifier.STATIC)
                        && element.getThrownTypes().isEmpty()
                        && element.getParameters().isEmpty())
                .collect(MoreCollectors.toOptional())
                .flatMap(element -> getPrimitiveSerializerMethodName(element.getReturnType()))
                .map(methodName -> ArgumentTypes.alias(typeName, methodName));
    }

    private ArgumentType getCustomType(TypeMirror typeMirror) {
        return ArgumentTypes.customType(TypeName.get(typeMirror));
    }
}
