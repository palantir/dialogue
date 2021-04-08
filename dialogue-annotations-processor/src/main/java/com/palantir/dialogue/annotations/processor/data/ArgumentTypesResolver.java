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

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.annotations.ParameterSerializer;
import com.palantir.dialogue.annotations.processor.ArgumentType;
import com.palantir.dialogue.annotations.processor.ArgumentType.OptionalType;
import com.palantir.dialogue.annotations.processor.ArgumentTypes;
import com.palantir.dialogue.annotations.processor.ImmutableOptionalType;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalInt;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public final class ArgumentTypesResolver {

    private static final ImmutableMap<TypeName, String> PARAMETER_SERIALIZER_TYPES;

    static {
        ImmutableMap.Builder<TypeName, String> builder = ImmutableMap.builder();
        for (Method method : ParameterSerializer.class.getDeclaredMethods()) {
            Preconditions.checkArgument(
                    method.getReturnType().equals(String.class),
                    "Return type is not String",
                    SafeArg.of("method", method));
            Preconditions.checkArgument(
                    method.getParameterCount() == 1,
                    "Serializer methods should have a single " + "arg",
                    SafeArg.of("method", method));

            ClassName parameterType = ClassName.get(Primitives.wrap(method.getParameterTypes()[0]));
            builder.put(parameterType, method.getName());
        }
        PARAMETER_SERIALIZER_TYPES = builder.build();
    }

    private final ResolverContext context;

    private final ArgumentType integerArgumentType;

    public ArgumentTypesResolver(ResolverContext context) {
        this.context = context;
        TypeName integerType = context.getTypeName(Integer.class);
        this.integerArgumentType = ArgumentTypes.primitive(integerType, planSerDeMethodName(integerType));
    }

    public Optional<ArgumentType> getArgumentType(VariableElement param) {
        return getArgumentTypeImpl(param, param.asType());
    }

    @SuppressWarnings("CyclomaticComplexity")
    private Optional<ArgumentType> getArgumentTypeImpl(Element paramContext, TypeMirror actualTypeMirror) {
        TypeName typeName = TypeName.get(actualTypeMirror);
        Optional<OptionalType> optionalType = getOptionalType(paramContext, actualTypeMirror);
        if (isPrimitive(typeName)) {
            return Optional.of(ArgumentTypes.primitive(typeName, planSerDeMethodName(typeName)));
        } else if (isRawRequestBody(actualTypeMirror)) {
            return Optional.of(ArgumentTypes.rawRequestBody(context.getTypeName(RequestBody.class)));
        } else if (optionalType.isPresent()) {
            // TODO(12345): We only want to go one level down: don't allow Optional<Optional<Type>>.
            return Optional.of(ArgumentTypes.optional(typeName, optionalType.get()));
        } else {
            return Optional.of(ArgumentTypes.customType(typeName));
        }
    }

    private boolean isPrimitive(TypeName in) {
        return PARAMETER_SERIALIZER_TYPES.containsKey(in.box());
    }

    private String planSerDeMethodName(TypeName in) {
        String typeName = PARAMETER_SERIALIZER_TYPES.get(in.box());
        return Preconditions.checkNotNull(typeName, "Unknown type");
    }

    private boolean isRawRequestBody(TypeMirror in) {
        return context.isSameTypes(in, RequestBody.class);
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
