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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.palantir.common.streams.KeyedStream;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.annotations.Json;
import com.palantir.dialogue.annotations.Request;
import com.palantir.dialogue.annotations.processor.ErrorContext;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.squareup.javapoet.TypeName;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.immutables.value.Value;

public final class ParamTypesResolver {

    private static final ImmutableSet<Class<?>> PARAM_ANNOTATION_CLASSES = ImmutableSet.of(
            Request.Body.class, Request.PathParam.class, Request.QueryParam.class, Request.Header.class);
    private static final ImmutableSet<String> PARAM_ANNOTATIONS =
            PARAM_ANNOTATION_CLASSES.stream().map(Class::getCanonicalName).collect(ImmutableSet.toImmutableSet());

    private final ErrorContext errorContext;
    private final Types types;
    private final TypeMirror requestBodyType;
    private final TypeMirror jsonDeserializerSerializerType;

    public ParamTypesResolver(ErrorContext errorContext, Elements elements, Types types) {
        this.errorContext = errorContext;
        this.types = types;
        this.requestBodyType =
                elements.getTypeElement(RequestBody.class.getCanonicalName()).asType();
        this.jsonDeserializerSerializerType =
                elements.getTypeElement(Json.class.getCanonicalName()).asType();
    }

    @SuppressWarnings("CyclomaticComplexity")
    public Optional<ParameterType> getParameterType(EndpointName endpointName, VariableElement variableElement) {
        List<AnnotationMirror> paramAnnotationMirrors = new ArrayList<>();
        for (AnnotationMirror annotationMirror : variableElement.getAnnotationMirrors()) {
            TypeElement annotationTypeElement =
                    MoreElements.asType(annotationMirror.getAnnotationType().asElement());
            if (PARAM_ANNOTATIONS.contains(
                    annotationTypeElement.getQualifiedName().toString())) {
                paramAnnotationMirrors.add(annotationMirror);
            }
        }

        if (paramAnnotationMirrors.isEmpty()) {
            if (types.isSameType(variableElement.asType(), requestBodyType)) {
                return Optional.of(ParameterTypes.rawBody());
            } else {
                errorContext.reportError(
                        "At least one annotation should be present or type should be RequestBody",
                        variableElement,
                        SafeArg.of("requestBody", requestBodyType),
                        SafeArg.of("supportedAnnotations", PARAM_ANNOTATION_CLASSES));
                return Optional.empty();
            }
        }

        if (paramAnnotationMirrors.size() > 1) {
            errorContext.reportError(
                    "Only single annotation can be used",
                    variableElement,
                    SafeArg.of("annotations", paramAnnotationMirrors));
            return Optional.empty();
        }

        // TODO(12345): More validation of values.

        AnnotationReflector annotationReflector =
                ImmutableAnnotationReflector.of(Iterables.getOnlyElement(paramAnnotationMirrors));
        if (annotationReflector.isAnnotation(Request.Body.class)) {
            // default annotation param values are not available at annotation processing time
            String serializerName = endpointName.get() + "Serializer";
            Optional<TypeMirror> customSerializer = annotationReflector.getValueMaybe(TypeMirror.class);
            // TODO(12345): Check that custom serializer has no-arg constructor and implements the right types that
            //  match
            return Optional.of(ParameterTypes.body(
                    TypeName.get(customSerializer.orElse(jsonDeserializerSerializerType)), serializerName));
        } else if (annotationReflector.isAnnotation(Request.Header.class)) {
            return Optional.of(ParameterTypes.header(annotationReflector.getStringValue()));
        } else if (annotationReflector.isAnnotation(Request.Header.class)) {
            return Optional.of(ParameterTypes.header(annotationReflector.getStringValue()));
        } else if (annotationReflector.isAnnotation(Request.PathParam.class)) {
            return Optional.of(ParameterTypes.path());
        } else if (annotationReflector.isAnnotation(Request.QueryParam.class)) {
            return Optional.of(ParameterTypes.query(annotationReflector.getValueStrict(String.class)));
        }

        throw new SafeIllegalStateException("Not possible");
    }

    @Value.Immutable
    interface AnnotationReflector {
        @Value.Parameter
        AnnotationMirror annotationMirror();

        @Value.Derived
        default TypeElement annotationTypeElement() {
            return MoreElements.asType(annotationMirror().getAnnotationType().asElement());
        }

        @Value.Derived
        default Map<String, Object> values() {
            return KeyedStream.stream(annotationMirror().getElementValues())
                    .mapKeys(key -> key.getSimpleName().toString())
                    .map(AnnotationValue::getValue)
                    .collectToMap();
        }

        default boolean isAnnotation(Class<? extends Annotation> annotationClazz) {
            return annotationTypeElement().getQualifiedName().contentEquals(annotationClazz.getCanonicalName());
        }

        default String getStringValue() {
            return getValueStrict(String.class);
        }

        default <T> Optional<T> getValueMaybe(Class<T> valueClazz) {
            Optional<Object> maybeValue = Optional.ofNullable(values().get("value"));
            return maybeValue.map(value -> {
                Preconditions.checkArgument(valueClazz.isInstance(value), "Value not of the right type");
                return (T) value;
            });
        }

        default <T> T getValueStrict(Class<T> valueClazz) {
            return getValueMaybe(valueClazz).orElseThrow(() -> new SafeIllegalStateException("Unknown value"));
        }
    }
}
