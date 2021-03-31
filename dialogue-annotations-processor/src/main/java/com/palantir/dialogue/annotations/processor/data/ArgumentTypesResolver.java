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
import com.google.common.collect.ImmutableMap;
import com.palantir.common.streams.KeyedStream;
import com.palantir.conjure.java.lib.SafeLong;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.annotations.processor.ArgumentType;
import com.palantir.dialogue.annotations.processor.ArgumentTypes;
import com.palantir.dialogue.annotations.processor.ErrorContext;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.ri.ResourceIdentifier;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class ArgumentTypesResolver {

    private static final String VALUE_OF_METHOD_NAME = "valueOf";

    private final ErrorContext errorContext;
    private final Elements elements;
    private final Types types;
    private final TypeMirror requestBodyType;
    private final TypeMirror stringType;

    /**
     * Why not generate this by inspecting the {@link com.palantir.dialogue.PlainSerDe} interface?
     * If we suddenly decide to provide special serialization for a widely used type, we could break wire
     * compatibility.
     */
    private static final ImmutableMap<TypeName, String> PLAIN_SER_DE_TYPES =
            ImmutableMap.copyOf(KeyedStream.stream(new ImmutableMap.Builder<Class<?>, String>()
                            // .put(PrimitiveType.Value.BEARERTOKEN, "BearerToken")
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

    public ArgumentTypesResolver(ErrorContext errorContext, Elements elements, Types types) {
        this.errorContext = errorContext;
        this.types = types;
        this.elements = elements;
        this.requestBodyType =
                elements.getTypeElement(RequestBody.class.getCanonicalName()).asType();
        this.stringType =
                elements.getTypeElement(String.class.getCanonicalName()).asType();
    }

    @SuppressWarnings("CyclomaticComplexity")
    public Optional<ArgumentType> getArgumentType(VariableElement param) {
        TypeMirror typeMirror = param.asType();
        TypeName typeName = TypeName.get(typeMirror);
        if (isPrimitive(typeName)) {
            return Optional.of(ArgumentTypes.primitive(typeName, planSerDeMethodName(typeName)));
        } else if (isRawRequestBody(typeMirror)) {
            return Optional.of(ArgumentTypes.rawRequestBody(TypeName.get(requestBodyType)));
        } else if (isCustomAndHasValueOfMethod(typeMirror)) {
            return Optional.of(ArgumentTypes.customType(typeName, VALUE_OF_METHOD_NAME));
        } else {
            errorContext.reportError("Cannot handle this type", param, SafeArg.of("type", typeName));
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
        Set<Modifier> modifiers = executableElement.getModifiers();
        return executableElement.getSimpleName().toString().equals(VALUE_OF_METHOD_NAME)
                && executableElement.getParameters().isEmpty()
                && executableElement.getTypeParameters().isEmpty()
                && types.isSameType(executableElement.getReturnType(), stringType)
                // TODO(12345): Maybe be nice and allow package private IFF in the same package?
                && modifiers.contains(Modifier.PUBLIC)
                && !modifiers.contains(Modifier.STATIC);
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
}
