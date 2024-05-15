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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.annotations.DefaultMultimapParamEncoder;
import com.palantir.dialogue.annotations.Json;
import com.palantir.dialogue.annotations.ListParamEncoder;
import com.palantir.dialogue.annotations.MultimapParamEncoder;
import com.palantir.dialogue.annotations.ParamEncoder;
import com.palantir.dialogue.annotations.Request;
import com.palantir.dialogue.annotations.Request.HeaderMap;
import com.palantir.dialogue.annotations.processor.data.ParameterEncoderType.EncoderType;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tokens.auth.AuthHeader;
import com.squareup.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public final class ParamTypesResolver {

    private static final ImmutableList<Class<?>> PARAM_ANNOTATION_CLASSES = ImmutableList.of(
            Request.Body.class,
            Request.PathParam.class,
            Request.QueryParam.class,
            Request.QueryMap.class,
            Request.Header.class,
            HeaderMap.class);
    private static final ImmutableSet<String> PARAM_ANNOTATIONS =
            PARAM_ANNOTATION_CLASSES.stream().map(Class::getCanonicalName).collect(ImmutableSet.toImmutableSet());
    private static final String paramEncoderMethod;
    private static final String listParamEncoderMethod;
    private static final String multimapParamEncoderMethod;

    static {
        try {
            paramEncoderMethod =
                    ParamEncoder.class.getMethod("toParamValue", Object.class).getName();
            listParamEncoderMethod = ListParamEncoder.class
                    .getMethod("toParamValues", Object.class)
                    .getName();
            multimapParamEncoderMethod = MultimapParamEncoder.class
                    .getMethod("toParamValues", Object.class)
                    .getName();
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
            if (context.isAssignable(variableElement.asType(), RequestBody.class)) {
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
            String serializerName = InstanceVariables.joinCamelCase(endpointName.get(), "Serializer");
            Optional<TypeMirror> customSerializer = annotationReflector.getValueFieldMaybe(TypeMirror.class);
            TypeMirror serializer = customSerializer.orElseGet(() -> context.getTypeMirror(Json.class));
            if (context.isAssignable(variableElement.asType(), BinaryRequestBody.class)
                    && context.isSameTypes(serializer, Json.class)) {
                context.reportError(
                        "BinaryRequestBody is not supported by default, prefer the more expressive RequestBody type",
                        variableElement);
                return Optional.empty();
            }

            // TODO(12345): Check that custom serializer has no-arg constructor and implements the right types that
            //  match
            return Optional.of(ParameterTypes.body(TypeName.get(serializer), serializerName));
        } else if (annotationReflector.isAnnotation(Request.Header.class)) {
            return Optional.of(ParameterTypes.header(
                    annotationReflector.getStringValueField(),
                    getParameterEncoder(
                            endpointName, variableElement, annotationReflector, EncoderTypeAndMethod.LIST)));
        } else if (annotationReflector.isAnnotation(Request.PathParam.class)) {
            return Optional.of(ParameterTypes.path(getPathParameterEncoder(
                    endpointName,
                    variableElement,
                    annotationReflector,
                    EncoderTypeAndMethod.PARAM,
                    EncoderTypeAndMethod.LIST)));
        } else if (annotationReflector.isAnnotation(Request.QueryParam.class)) {
            return Optional.of(ParameterTypes.query(
                    annotationReflector.getValueStrict(String.class),
                    getParameterEncoder(
                            endpointName, variableElement, annotationReflector, EncoderTypeAndMethod.LIST)));
        } else if (annotationReflector.isAnnotation(Request.QueryMap.class)) {
            // we always want a parameter encoder for map types because it enables us to get compile
            // time safety with the generated code, since we cannot get the default value from the annotation
            // in this code, fall back to what the default would be
            ParameterEncoderType customEncoderType = getParameterEncoder(
                            endpointName, variableElement, annotationReflector, EncoderTypeAndMethod.MULTIMAP)
                    .orElseGet(() -> multimapDefaultEncoder(endpointName, variableElement));
            return Optional.of(ParameterTypes.queryMap(customEncoderType));
        } else if (annotationReflector.isAnnotation(Request.HeaderMap.class)) {
            ParameterEncoderType customEncoderType = getParameterEncoder(
                            endpointName, variableElement, annotationReflector, EncoderTypeAndMethod.MULTIMAP)
                    .orElseGet(() -> multimapDefaultEncoder(endpointName, variableElement));
            return Optional.of(ParameterTypes.headerMap(customEncoderType));
        }

        throw new SafeIllegalStateException("Not possible");
    }

    private ParameterEncoderType multimapDefaultEncoder(EndpointName endpointName, VariableElement variableElement) {
        return ImmutableParameterEncoderType.builder()
                .type(EncoderTypeAndMethod.MULTIMAP.encoderType)
                .encoderJavaType(TypeName.get(DefaultMultimapParamEncoder.class))
                .encoderFieldName(InstanceVariables.joinCamelCase(
                        endpointName.get(), variableElement.getSimpleName().toString(), "Encoder"))
                .encoderMethodName(EncoderTypeAndMethod.MULTIMAP.method)
                .build();
    }

    private Optional<ParameterEncoderType> getPathParameterEncoder(
            EndpointName endpointName,
            VariableElement variableElement,
            AnnotationReflector annotationReflector,
            EncoderTypeAndMethod encoderTypeAndMethod,
            EncoderTypeAndMethod listEncoderTypeAndMethod) {
        Optional<TypeName> encoderTypeName =
                annotationReflector.getFieldMaybe("encoder", TypeMirror.class).map(TypeName::get);

        Optional<TypeName> listEncoderTypeName = annotationReflector
                .getFieldMaybe("listEncoder", TypeMirror.class)
                .map(TypeName::get);

        if (encoderTypeName.isPresent() && listEncoderTypeName.isPresent()) {
            context.reportError("Only one of encoder and listEncoder can be set", variableElement);
            return Optional.empty();
        }

        if (encoderTypeName.isPresent()) {
            return getParameterEncoder(endpointName, variableElement, encoderTypeName, encoderTypeAndMethod);
        }

        if (listEncoderTypeName.isPresent()) {
            return getParameterEncoder(endpointName, variableElement, listEncoderTypeName, listEncoderTypeAndMethod);
        }

        return Optional.empty();
    }

    private Optional<ParameterEncoderType> getParameterEncoder(
            EndpointName endpointName,
            VariableElement variableElement,
            AnnotationReflector annotationReflector,
            EncoderTypeAndMethod encoderTypeAndMethod) {
        return getParameterEncoder(
                endpointName,
                variableElement,
                annotationReflector.getFieldMaybe("encoder", TypeMirror.class).map(TypeName::get),
                encoderTypeAndMethod);
    }

    private Optional<ParameterEncoderType> getParameterEncoder(
            EndpointName endpointName,
            VariableElement variableElement,
            Optional<TypeName> typeName,
            EncoderTypeAndMethod encoderTypeAndMethod) {
        return typeName.map(encoderJavaType -> ImmutableParameterEncoderType.builder()
                .type(encoderTypeAndMethod.encoderType)
                .encoderJavaType(encoderJavaType)
                .encoderFieldName(InstanceVariables.joinCamelCase(
                        endpointName.get(), variableElement.getSimpleName().toString(), "Encoder"))
                .encoderMethodName(encoderTypeAndMethod.method)
                .build());
    }

    @SuppressWarnings("ImmutableEnumChecker")
    private enum EncoderTypeAndMethod {
        PARAM(EncoderTypes.param(), paramEncoderMethod),
        LIST(EncoderTypes.listParam(), listParamEncoderMethod),
        MULTIMAP(EncoderTypes.multimapParam(), multimapParamEncoderMethod);

        private final ParameterEncoderType.EncoderType encoderType;
        private final String method;

        EncoderTypeAndMethod(EncoderType encoderType, String method) {
            this.encoderType = encoderType;
            this.method = method;
        }
    }
}
