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
import com.google.common.collect.Multimap;
import com.palantir.common.streams.KeyedStream;
import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.annotations.processor.data.ArgumentType.OptionalType;
import com.palantir.logsafe.Preconditions;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;
import javax.lang.model.element.Element;
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
                            .build())
                    .mapKeys((Function<Class<?>, ClassName>) ClassName::get)
                    .map(value -> "serialize" + value)
                    .collectToMap());

    private final ResolverContext context;

    private final ArgumentType integerArgumentType;

    public ArgumentTypesResolver(ResolverContext context) {
        this.context = context;
        TypeName integerType = context.getTypeName(Integer.class);
        this.integerArgumentType =
                ArgumentTypes.primitive(integerType, planSerDeMethodName(integerType), Optional.empty());
    }

    public Optional<ArgumentType> getArgumentType(VariableElement param) {
        return getArgumentTypeImpl(param, param.asType());
    }

    @SuppressWarnings("CyclomaticComplexity")
    private Optional<ArgumentType> getArgumentTypeImpl(Element paramContext, TypeMirror actualTypeMirror) {
        TypeName typeName = TypeName.get(actualTypeMirror);
        Optional<OptionalType> optionalType = getOptionalType(paramContext, actualTypeMirror);
        Optional<TypeMirror> listType = getListType(actualTypeMirror);
        Optional<ArgumentType> mapType = getMapType(actualTypeMirror, typeName);
        if (isPrimitive(typeName)) {
            return Optional.of(ArgumentTypes.primitive(typeName, planSerDeMethodName(typeName), Optional.empty()));
        } else if (listType.map(innerType -> isPrimitive(TypeName.get(innerType)))
                .orElse(false)) {
            TypeName innerTypeName = TypeName.get(listType.get());
            return Optional.of(
                    ArgumentTypes.primitive(typeName, planSerDeMethodName(innerTypeName), Optional.of(innerTypeName)));
        } else if (isRawRequestBody(actualTypeMirror)) {
            return Optional.of(ArgumentTypes.rawRequestBody(TypeName.get(actualTypeMirror)));
        } else if (optionalType.isPresent()) {
            // TODO(12345): We only want to go one level down: don't allow Optional<Optional<Type>>.
            return Optional.of(ArgumentTypes.optional(typeName, optionalType.get()));
        } else if (mapType.isPresent()) {
            return mapType;
        } else {
            return Optional.of(ArgumentTypes.customType(typeName));
        }
    }

    private boolean isPrimitive(TypeName in) {
        return PARAMETER_SERIALIZER_TYPES.containsKey(in.box());
    }

    private Optional<TypeMirror> getListType(TypeMirror in) {
        return context.getGenericInnerType(List.class, in);
    }

    private Optional<ArgumentType> getMapType(TypeMirror in, TypeName typeName) {
        return context.maybeAsDeclaredType(in).flatMap(declaredType -> {
            if (context.isAssignableWithErasure(declaredType, Multimap.class)) {
                return Optional.of(ArgumentTypes.mapType(typeName));
            } else if (context.isAssignableWithErasure(declaredType, Map.class)) {
                return Optional.of(ArgumentTypes.mapType(typeName));
            }
            return Optional.of(ArgumentTypes.customType(typeName));
        });
    }

    private String planSerDeMethodName(TypeName in) {
        String typeName = PARAMETER_SERIALIZER_TYPES.get(in.box());
        return Preconditions.checkNotNull(typeName, "Unknown type");
    }

    private boolean isRawRequestBody(TypeMirror in) {
        return context.isAssignable(in, RequestBody.class);
    }

    private Optional<OptionalType> getOptionalType(Element paramContext, TypeMirror typeName) {
        if (context.isSameTypes(typeName, OptionalInt.class)) {
            return Optional.of(ImmutableOptionalType.builder()
                    .isPresentMethodName("isPresent")
                    .valueGetMethodName("getAsInt")
                    .underlyingType(integerArgumentType)
                    .build());
        }

        return context.getGenericInnerType(Optional.class, typeName)
                .flatMap(innerType -> getArgumentTypeImpl(paramContext, innerType)
                        .map(argumentType -> ImmutableOptionalType.builder()
                                .isPresentMethodName("isPresent")
                                .valueGetMethodName("get")
                                .underlyingType(argumentType)
                                .build()));
    }
}
