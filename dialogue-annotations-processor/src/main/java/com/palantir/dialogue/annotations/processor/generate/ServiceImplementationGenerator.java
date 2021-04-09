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

package com.palantir.dialogue.annotations.processor.generate;

import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.annotations.DefaultParameterSerializer;
import com.palantir.dialogue.annotations.ParameterSerializer;
import com.palantir.dialogue.annotations.processor.data.ArgumentDefinition;
import com.palantir.dialogue.annotations.processor.data.ArgumentType;
import com.palantir.dialogue.annotations.processor.data.ArgumentType.OptionalType;
import com.palantir.dialogue.annotations.processor.data.ArgumentTypes;
import com.palantir.dialogue.annotations.processor.data.EndpointDefinition;
import com.palantir.dialogue.annotations.processor.data.ParameterEncoderType;
import com.palantir.dialogue.annotations.processor.data.ParameterEncoderType.EncoderType;
import com.palantir.dialogue.annotations.processor.data.ParameterType.Cases;
import com.palantir.dialogue.annotations.processor.data.ParameterTypes;
import com.palantir.dialogue.annotations.processor.data.ReturnType;
import com.palantir.dialogue.annotations.processor.data.ServiceDefinition;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;

public final class ServiceImplementationGenerator {

    private static final String REQUEST = "_request";
    private static final String PARAMETER_SERIALIZER = "_parameterSerializer";

    private final ServiceDefinition serviceDefinition;

    public ServiceImplementationGenerator(ServiceDefinition serviceDefinition) {
        this.serviceDefinition = serviceDefinition;
    }

    public TypeSpec generate() {
        TypeSpec.Builder impl =
                TypeSpec.anonymousClassBuilder("").addSuperinterface(serviceDefinition.serviceInterface());

        impl.addField(FieldSpec.builder(ParameterSerializer.class, PARAMETER_SERIALIZER)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer(CodeBlock.of("$T.INSTANCE", DefaultParameterSerializer.class))
                .build());

        serviceDefinition.endpoints().forEach(endpoint -> {
            endpoint.arguments().stream()
                    .flatMap(arg -> ParameterTypes.caseOf(arg.paramType())
                            .body((serializer, serializerFieldName) ->
                                    Optional.of(serializer(arg, serializer, serializerFieldName)))
                            .header((_headerName, headerParamEncoder) -> headerParamEncoder.map(this::encoder))
                            .path(parameterEncoderType -> parameterEncoderType.map(this::encoder))
                            .query((_paramName, paramEncoder) -> paramEncoder.map(this::encoder))
                            .otherwise_(Optional.empty())
                            .stream())
                    .forEach(impl::addField);
            impl.addField(bindEndpointChannel(endpoint));
            impl.addMethod(clientImpl(endpoint));

            deserializer(endpoint.returns()).ifPresent(impl::addField);
        });

        return impl.build();
    }

    private MethodSpec clientImpl(EndpointDefinition def) {
        List<ParameterSpec> params = def.arguments().stream()
                .map(arg -> ParameterSpec.builder(
                                ArgumentTypes.caseOf(arg.argType())
                                        .primitive((javaTypeName, _unused) -> javaTypeName)
                                        .rawRequestBody(typeName -> typeName)
                                        .optional((optionalJavaType, _unused) -> optionalJavaType)
                                        .customType(typeName -> typeName),
                                arg.argName().get())
                        .build())
                .collect(Collectors.toList());

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(
                        def.endpointName().get())
                .addModifiers(Modifier.PUBLIC)
                .addParameters(params)
                .addAnnotation(Override.class);

        methodBuilder.addCode("$T $L = $T.builder();", Request.Builder.class, REQUEST, Request.class);

        def.arguments().forEach(arg -> generateParam(arg).ifPresent(methodBuilder::addCode));

        methodBuilder.returns(def.returns().returnType());

        boolean isAsync = def.returns().asyncInnerType().isPresent();

        String executeCode =
                isAsync ? "$L.clients().call($L, $L.build(), $L);" : "$L.clients().callBlocking($L, $L.build(), $L);";
        CodeBlock execute = CodeBlock.of(
                executeCode,
                serviceDefinition.conjureRuntimeArgName(),
                def.channelFieldName(),
                REQUEST,
                def.returns().deserializerFieldName());
        methodBuilder.addCode(!def.returns().isVoid() || isAsync ? "return $L" : "$L", execute);

        return methodBuilder.build();
    }

    private FieldSpec bindEndpointChannel(EndpointDefinition endpoint) {
        return FieldSpec.builder(ClassName.get(EndpointChannel.class), endpoint.channelFieldName())
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer(
                        "$L.endpoint($T.$L)",
                        serviceDefinition.endpointChannelFactoryArgName(),
                        serviceDefinition.endpointsEnum(),
                        endpoint.endpointName().get())
                .build();
    }

    private FieldSpec serializer(
            ArgumentDefinition argumentDefinition, TypeName serializerType, String serializerFieldName) {
        TypeName className = ArgumentTypes.caseOf(argumentDefinition.argType())
                .primitive((javaTypeName, _unused) -> javaTypeName)
                .customType(typeName -> typeName)
                .otherwiseEmpty()
                .get();
        ParameterizedTypeName deserializerType = ParameterizedTypeName.get(ClassName.get(Serializer.class), className);
        return FieldSpec.builder(deserializerType, serializerFieldName)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T().serializerFor(new $T<$T>() {})", serializerType, TypeMarker.class, className)
                .build();
    }

    private Optional<FieldSpec> deserializer(ReturnType type) {
        TypeName fullReturnType = type.returnType().box();
        TypeName deserializerFactoryType = type.deserializerFactory();
        TypeName innerType = type.asyncInnerType().map(TypeName::box).orElse(fullReturnType);
        ParameterizedTypeName deserializerType =
                ParameterizedTypeName.get(ClassName.get(Deserializer.class), innerType);

        CodeBlock realDeserializer = CodeBlock.of(
                "new $T().deserializerFor(new $T<$T>() {})", deserializerFactoryType, TypeMarker.class, innerType);
        CodeBlock voidDeserializer =
                CodeBlock.of("$L.bodySerDe().emptyBodyDeserializer()", serviceDefinition.conjureRuntimeArgName());

        return Optional.of(FieldSpec.builder(deserializerType, type.deserializerFieldName())
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer(type.isVoid() ? voidDeserializer : realDeserializer)
                .build());
    }

    private FieldSpec encoder(ParameterEncoderType type) {
        // TODO(12345): Don't be cheeky, create the right parameterized interface.
        return FieldSpec.builder(type.encoderJavaType(), type.encoderFieldName())
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer(CodeBlock.of("new $T()", type.encoderJavaType()))
                .build();
    }

    private Optional<CodeBlock> generateParam(ArgumentDefinition param) {
        return param.paramType().match(new Cases<>() {
            @Override
            public Optional<CodeBlock> rawBody() {
                return Optional.of(
                        CodeBlock.of("$L.body($L);", REQUEST, param.argName().get()));
            }

            @Override
            public Optional<CodeBlock> body(TypeName _serializerFactory, String serializerFieldName) {
                return Optional.of(CodeBlock.of(
                        "$L.body($L.serialize($L));",
                        REQUEST,
                        serializerFieldName,
                        param.argName().get()));
            }

            @Override
            public Optional<CodeBlock> header(String headerName, Optional<ParameterEncoderType> headerParamEncoder) {
                return generateHeaderParam(param, headerName, headerParamEncoder);
            }

            @Override
            public Optional<CodeBlock> path(Optional<ParameterEncoderType> paramEncoder) {
                return generatePathParam(param, paramEncoder);
            }

            @Override
            public Optional<CodeBlock> query(String paramName, Optional<ParameterEncoderType> paramEncoder) {
                return generateQueryParam(param, paramName, paramEncoder);
            }
        });
    }

    private Optional<CodeBlock> generateHeaderParam(
            ArgumentDefinition param, String headerName, Optional<ParameterEncoderType> headerParamEncoder) {
        return generatePlainSerializer(
                "putHeaderParams",
                "putAllHeaderParams",
                headerName,
                CodeBlock.of(param.argName().get()),
                param.argType(),
                headerParamEncoder);
    }

    private Optional<CodeBlock> generatePathParam(
            ArgumentDefinition param, Optional<ParameterEncoderType> paramEncoder) {
        return generatePlainSerializer(
                "putPathParams",
                "nope",
                param.argName().get(),
                CodeBlock.of("$L", param.argName().get()),
                param.argType(),
                paramEncoder);
    }

    private Optional<CodeBlock> generateQueryParam(
            ArgumentDefinition param, String paramName, Optional<ParameterEncoderType> paramEncoder) {
        return generatePlainSerializer(
                "putQueryParams",
                "putAllQueryParams",
                paramName,
                CodeBlock.of(param.argName().get()),
                param.argType(),
                paramEncoder);
    }

    private Optional<CodeBlock> generatePlainSerializer(
            String singleValueMethod,
            String multiValueMethod,
            String key,
            CodeBlock argName,
            ArgumentType type,
            Optional<ParameterEncoderType> maybeParameterEncoderType) {
        return type.match(new ArgumentType.Cases<>() {
            @Override
            public Optional<CodeBlock> primitive(TypeName _unused, String parameterSerializerMethodName) {
                return Optional.of(CodeBlock.of(
                        "$L.$L($S, $L.$L($L));",
                        REQUEST,
                        singleValueMethod,
                        key,
                        PARAMETER_SERIALIZER,
                        parameterSerializerMethodName,
                        argName));
            }

            @Override
            public Optional<CodeBlock> rawRequestBody(TypeName _unused) {
                throw new UnsupportedOperationException("This should not happen");
            }

            @Override
            public Optional<CodeBlock> optional(TypeName _unused, OptionalType optionalType) {
                return generatePlainSerializer(
                                singleValueMethod,
                                multiValueMethod,
                                key,
                                CodeBlock.of("$L.$L()", argName, optionalType.valueGetMethodName()),
                                optionalType.underlyingType(),
                                maybeParameterEncoderType)
                        .map(inner -> CodeBlock.builder()
                                .beginControlFlow("if ($L.$L())", argName, optionalType.isPresentMethodName())
                                .add(inner)
                                .endControlFlow()
                                .build());
            }

            @Override
            public Optional<CodeBlock> customType(TypeName _typeName) {
                if (maybeParameterEncoderType.isEmpty()) {
                    return Optional.empty();
                }

                return maybeParameterEncoderType.flatMap(parameterEncoderType -> parameterEncoderType
                        .type()
                        .match(new EncoderType.Cases<Optional<CodeBlock>>() {
                            @Override
                            public Optional<CodeBlock> param() {
                                return Optional.of(CodeBlock.of(
                                        "$L.$L($S, $L.$L($L));",
                                        REQUEST,
                                        singleValueMethod,
                                        key,
                                        parameterEncoderType.encoderFieldName(),
                                        parameterEncoderType.encoderMethodName(),
                                        argName));
                            }

                            @Override
                            public Optional<CodeBlock> listParam() {
                                return Optional.of(CodeBlock.of(
                                        "$L.$L($S, $L.$L($L));",
                                        REQUEST,
                                        multiValueMethod,
                                        key,
                                        parameterEncoderType.encoderFieldName(),
                                        parameterEncoderType.encoderMethodName(),
                                        argName));
                            }
                        }));
            }
        });
    }
}
