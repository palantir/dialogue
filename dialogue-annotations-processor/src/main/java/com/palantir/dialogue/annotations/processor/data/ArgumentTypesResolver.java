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

import com.google.auto.common.MoreElements;
import com.google.auto.common.Visibility;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.palantir.common.streams.KeyedStream;
import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.annotations.processor.ArgumentType;
import com.palantir.dialogue.annotations.processor.ArgumentType.OptionalType;
import com.palantir.dialogue.annotations.processor.ArgumentTypes;
import com.palantir.dialogue.annotations.processor.ErrorContext;
import com.palantir.dialogue.annotations.processor.ImmutableOptionalType;
import com.palantir.logsafe.Preconditions;
import com.palantir.ri.ResourceIdentifier;
import com.palantir.tokens.auth.BearerToken;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class ArgumentTypesResolver {

    private static final String TO_STRING_PARAM_VALUE_METHOD = "toStringParamValue";

    /**
     * Why not generate this by inspecting the {@link com.palantir.dialogue.PlainSerDe} interface?
     * If we suddenly decide to provide special serialization for a widely used type, we could break wire
     * compatibility.
     */
    private static final ImmutableMap<TypeName, String> PLAIN_SER_DE_TYPES =
            ImmutableMap.copyOf(KeyedStream.stream(new ImmutableMap.Builder<Class<?>, String>()
                            .put(BearerToken.class, "BearerToken")
                            .put(Boolean.class, "Boolean")
                            .put(OffsetDateTime.class, "DateTime")
                            .put(Double.class, "Double")
                            .put(Integer.class, "Integer")
                            .put(ResourceIdentifier.class, "Rid")
                            .put(SafeLong.class, "SafeLong")
                            .put(String.class, "String")
                            .put(UUID.class, "Uuid")
                            .build())
                    .mapKeys((Function<Class<?>, ClassName>) ClassName::get)
                    .map(value -> "serialize" + value)
                    .collectToMap());

    @SuppressWarnings("StrictUnusedVariable")
    private final ErrorContext errorContext;

    private final Elements elements;
    private final Types types;

    private final TypeMirror requestBodyType;
    private final TypeMirror stringType;
    private final TypeElement genericOptionalType;
    private final TypeMirror optionalIntType;

    private final ArgumentType integerArgumentType;

    public ArgumentTypesResolver(ErrorContext errorContext, Elements elements, Types types) {
        this.errorContext = errorContext;
        this.types = types;
        this.elements = elements;
        this.requestBodyType =
                elements.getTypeElement(RequestBody.class.getCanonicalName()).asType();
        this.stringType =
                elements.getTypeElement(String.class.getCanonicalName()).asType();

        this.genericOptionalType = elements.getTypeElement(Optional.class.getCanonicalName());
        this.optionalIntType =
                elements.getTypeElement(OptionalInt.class.getCanonicalName()).asType();
        TypeMirror integerType =
                elements.getTypeElement(Integer.class.getCanonicalName()).asType();
        this.integerArgumentType =
                ArgumentTypes.primitive(TypeName.get(integerType), planSerDeMethodName(TypeName.get(integerType)));
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
            return Optional.of(ArgumentTypes.rawRequestBody(TypeName.get(requestBodyType)));
        } else if (optionalType.isPresent()) {
            // TODO(12345): We only want to go one level down: don't allow Optional<Optional<Type>>.
            return Optional.of(ArgumentTypes.optional(typeName, optionalType.get()));
        } else if (isCustomAndHasValueOfMethod(actualTypeMirror)) {
            return Optional.of(ArgumentTypes.customTypeWithValueOf(typeName, TO_STRING_PARAM_VALUE_METHOD));
        } else {
            return Optional.of(ArgumentTypes.customType(typeName));
        }
    }

    private boolean isPrimitive(TypeName in) {
        return PLAIN_SER_DE_TYPES.containsKey(in.box());
    }

    private String planSerDeMethodName(TypeName in) {
        String typeName = PLAIN_SER_DE_TYPES.get(in.box());
        return Preconditions.checkNotNull(typeName, "Unknown type");
    }

    private boolean isRawRequestBody(TypeMirror in) {
        return types.isSameType(in, requestBodyType);
    }

    private Optional<OptionalType> getOptionalType(Element paramContext, TypeMirror typeName) {
        // "They tryin'a make me use a visitor, but I say: NO, NO NO!"
        if (!(typeName instanceof DeclaredType)) {
            return Optional.empty();
        }
        DeclaredType declaredType = (DeclaredType) typeName;
        if (types.isSameType(declaredType, optionalIntType)) {
            return Optional.of(ImmutableOptionalType.builder()
                    .isPresentMethodName("isPresent")
                    .valueGetMethodName("getAsInt")
                    .underlyingType(integerArgumentType)
                    .build());
        }

        if (declaredType.getTypeArguments().size() != 1) {
            return Optional.empty();
        }

        TypeMirror innerType = Iterables.getOnlyElement(declaredType.getTypeArguments());
        DeclaredType genericOptional = types.getDeclaredType(genericOptionalType, innerType);

        if (types.isSameType(genericOptional, declaredType)) {
            return getArgumentTypeImpl(paramContext, innerType).map(argumentType -> ImmutableOptionalType.builder()
                    .isPresentMethodName("isPresent")
                    .valueGetMethodName("get")
                    .underlyingType(argumentType)
                    .build());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Checks if type has method {@code public String valueOf()}.
     */
    private boolean isCustomAndHasValueOfMethod(TypeMirror typeMirror) {
        Element element = types.asElement(typeMirror);
        // Unclear if this is idiomatic?
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        return MoreElements.getAllMethods(typeElement, types, elements).stream().anyMatch(this::isValueOfMethod);
    }

    private boolean isValueOfMethod(ExecutableElement executableElement) {
        Visibility visibility = Visibility.ofElement(executableElement);
        Set<Modifier> modifiers = executableElement.getModifiers();
        // TODO(12345): Actually give the user good feedback on what exactly they need to add to make this type comply.
        return executableElement.getSimpleName().toString().equals(TO_STRING_PARAM_VALUE_METHOD)
                && executableElement.getParameters().isEmpty()
                && executableElement.getTypeParameters().isEmpty()
                && types.isSameType(executableElement.getReturnType(), stringType)
                // TODO(12345): Maybe be nice and allow package private IFF in the same package?
                && visibility.equals(Visibility.PUBLIC)
                && !modifiers.contains(Modifier.STATIC);
    }
}
