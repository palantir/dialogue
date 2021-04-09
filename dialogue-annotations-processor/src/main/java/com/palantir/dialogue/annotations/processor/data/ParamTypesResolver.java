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
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.annotations.HeaderParamEncoder;
import com.palantir.dialogue.annotations.Json;
import com.palantir.dialogue.annotations.ParamEncoder;
import com.palantir.dialogue.annotations.Request;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tokens.auth.AuthHeader;
import com.squareup.javapoet.TypeName;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public final class ParamTypesResolver {

    private static final ImmutableSet<Class<?>> PARAM_ANNOTATION_CLASSES = ImmutableSet.of(
            Request.Body.class, Request.PathParam.class, Request.QueryParam.class, Request.Header.class);
    private static final ImmutableSet<String> PARAM_ANNOTATIONS =
            PARAM_ANNOTATION_CLASSES.stream().map(Class::getCanonicalName).collect(ImmutableSet.toImmutableSet());
    private static final Method paramEncoderMethod;
    private static final Method headerParamEncoderMethod;

    static {
        try {
            paramEncoderMethod = ParamEncoder.class.getMethod("toParamValue", Object.class);
            headerParamEncoderMethod = HeaderParamEncoder.class.getMethod("toHeaderParamValues", Object.class);
        } catch (NoSuchMethodException e) {
            throw new SafeRuntimeException("Method renamed: are you sure you want to cause a break?", e);
        }
    }

    private final ResolverContext context;

    public ParamTypesResolver(ResolverContext context) {
        this.context = context;
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
            if (context.isSameTypes(variableElement.asType(), RequestBody.class)) {
                return Optional.of(ParameterTypes.rawBody());
            } else if (context.isSameTypes(variableElement.asType(), AuthHeader.class)) {
                return Optional.of(ParameterTypes.header("Authorization", Optional.empty()));
            } else {
                context.reportError(
                        "At least one annotation should be present or type should be RequestBody",
                        variableElement,
                        SafeArg.of("requestBody", RequestBody.class),
                        SafeArg.of("supportedAnnotations", PARAM_ANNOTATION_CLASSES));
                return Optional.empty();
            }
        }

        if (paramAnnotationMirrors.size() > 1) {
            context.reportError(
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
            Optional<TypeMirror> customSerializer = annotationReflector.getValueFieldMaybe(TypeMirror.class);
            // TODO(12345): Check that custom serializer has no-arg constructor and implements the right types that
            //  match
            return Optional.of(ParameterTypes.body(
                    TypeName.get(customSerializer.orElseGet(() -> context.getTypeMirror(Json.class))), serializerName));
        } else if (annotationReflector.isAnnotation(Request.Header.class)) {
            return Optional.of(ParameterTypes.header(
                    annotationReflector.getStringValueField(),
                    getParameterEncoder(
                            endpointName,
                            variableElement,
                            annotationReflector,
                            EncoderTypes.headerParam(),
                            paramEncoderMethod)));
        } else if (annotationReflector.isAnnotation(Request.PathParam.class)) {
            return Optional.of(ParameterTypes.path(getParameterEncoder(
                    endpointName, variableElement, annotationReflector, EncoderTypes.param(), paramEncoderMethod)));
        } else if (annotationReflector.isAnnotation(Request.QueryParam.class)) {
            return Optional.of(ParameterTypes.query(
                    annotationReflector.getValueStrict(String.class),
                    getParameterEncoder(
                            endpointName,
                            variableElement,
                            annotationReflector,
                            EncoderTypes.param(),
                            paramEncoderMethod)));
        }

        throw new SafeIllegalStateException("Not possible");
    }

    private Optional<ParameterEncoderType> getParameterEncoder(
            EndpointName endpointName,
            VariableElement variableElement,
            AnnotationReflector annotationReflector,
            ParameterEncoderType.EncoderType encoderType,
            Method encoderMethod) {
        return annotationReflector
                .getFieldMaybe("encoder", TypeMirror.class)
                .map(TypeName::get)
                .map(encoderJavaType -> ImmutableParameterEncoderType.builder()
                        .type(encoderType)
                        .encoderJavaType(encoderJavaType)
                        .encoderFieldName(InstanceVariables.joinCamelCase(
                                endpointName.get(),
                                variableElement.getSimpleName().toString(),
                                "Encoder"))
                        .encoderMethodName(encoderMethod.getName())
                        .build());
    }
}
