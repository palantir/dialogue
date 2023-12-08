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
import com.palantir.dialogue.annotations.ErrorHandlingDeserializerFactory;
import com.palantir.dialogue.annotations.ErrorHandlingVoidDeserializer;
import com.palantir.dialogue.annotations.ParameterSerializer;
import com.palantir.dialogue.annotations.processor.data.ArgumentDefinition;
import com.palantir.dialogue.annotations.processor.data.ArgumentType;
import com.palantir.dialogue.annotations.processor.data.ArgumentType.ListType;
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
                            .header((_headerName, maybeEncoder) -> maybeEncoder.map(this::encoder))
                            .path(maybeEncoder -> maybeEncoder.map(this::encoder))
                            .query((_paramName, maybeEncoder) -> maybeEncoder.map(this::encoder))
                            .queryMap(encoder -> Optional.of(encoder(encoder)))
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
                                        .primitive((typeName, _parameterSerializerMethodName) -> typeName)
                                        .list((typeName, _parameterSerializerMethodName) -> typeName)
                                        .alias((typeName, _aliasType) -> typeName)
                                        .optional((typeName, _optionalType) -> typeName)
                                        .enumType((typeName, _optionalType) -> typeName)
                                        .rawRequestBody(typeName -> typeName)
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

        def.arguments().forEach(arg -> methodBuilder.addCode(generateParam(arg)));

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
                .primitive((typeName, _parameterSerializerMethodName) -> typeName)
                .list((typeName, _parameterSerializerMethodName) -> typeName)
                .alias((typeName, _parameterSerializerMethodName) -> typeName)
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
        TypeName errorDecoderType = type.errorDecoder();
        TypeName innerType = type.asyncInnerType().map(TypeName::box).orElse(fullReturnType);
        ParameterizedTypeName deserializerType =
                ParameterizedTypeName.get(ClassName.get(Deserializer.class), innerType);

        CodeBlock realDeserializer = CodeBlock.of(
                "new $T<>(new $T(), new $T()).deserializerFor(new $T<$T>() {})",
                ErrorHandlingDeserializerFactory.class,
                deserializerFactoryType,
                errorDecoderType,
                TypeMarker.class,
                innerType);
        CodeBlock voidDeserializer = CodeBlock.of(
                "new $T($L.bodySerDe().emptyBodyDeserializer(), new $T())",
                ErrorHandlingVoidDeserializer.class,
                serviceDefinition.conjureRuntimeArgName(),
                errorDecoderType);
        return Optional.of(FieldSpec.builder(deserializerType, type.deserializerFieldName())
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer(type.isVoid() ? voidDeserializer : realDeserializer)
                .build());
    }

    private FieldSpec encoder(ParameterEncoderType type) {
        return FieldSpec.builder(type.encoderJavaType(), type.encoderFieldName())
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer(CodeBlock.of("new $T()", type.encoderJavaType()))
                .build();
    }

    private CodeBlock generateParam(ArgumentDefinition param) {
        return param.paramType().match(new Cases<>() {
            @Override
            public CodeBlock rawBody() {
                return CodeBlock.of("$L.body($L);", REQUEST, param.argName().get());
            }

            @Override
            public CodeBlock body(TypeName _serializerFactory, String serializerFieldName) {
                return CodeBlock.of(
                        "$L.body($L.serialize($L));",
                        REQUEST,
                        serializerFieldName,
                        param.argName().get());
            }

            @Override
            public CodeBlock header(String headerName, Optional<ParameterEncoderType> paramEncoderType) {
                return generateHeaderParam(param, headerName, paramEncoderType);
            }

            @Override
            public CodeBlock path(Optional<ParameterEncoderType> paramEncoderType) {
                return generatePathParam(param, paramEncoderType);
            }

            @Override
            public CodeBlock query(String paramName, Optional<ParameterEncoderType> paramEncoderType) {
                return generateQueryParam(param, paramName, paramEncoderType);
            }

            @Override
            public CodeBlock queryMap(ParameterEncoderType parameterEncoderType) {
                return generateQueryMapParam(param, parameterEncoderType);
            }
        });
    }

    private CodeBlock generateHeaderParam(
            ArgumentDefinition param, String headerName, Optional<ParameterEncoderType> headerParamEncoder) {
        return generatePlainSerializer(
                "putHeaderParams",
                "putAllHeaderParams",
                headerName,
                CodeBlock.of(param.argName().get()),
                param.argType(),
                headerParamEncoder);
    }

    private CodeBlock generatePathParam(ArgumentDefinition param, Optional<ParameterEncoderType> paramEncoder) {
        return generatePlainSerializer(
                "putPathParams",
                "putAllPathParams",
                param.argName().get(),
                CodeBlock.of("$L", param.argName().get()),
                param.argType(),
                paramEncoder);
    }

    private CodeBlock generateQueryParam(
            ArgumentDefinition param, String paramName, Optional<ParameterEncoderType> paramEncoder) {
        return generatePlainSerializer(
                "putQueryParams",
                "putAllQueryParams",
                paramName,
                CodeBlock.of(param.argName().get()),
                param.argType(),
                paramEncoder);
    }

    private CodeBlock generateQueryMapParam(ArgumentDefinition param, ParameterEncoderType paramEncoder) {
        return generatePlainSerializer(
                "nope",
                "putAllQueryParams",
                param.argName().get(),
                CodeBlock.of("$L", param.argName().get()),
                param.argType(),
                Optional.of(paramEncoder));
    }

    private CodeBlock generatePlainSerializer(
            String singleValueMethod,
            String multiValueMethod,
            String key,
            CodeBlock argName,
            ArgumentType type,
            Optional<ParameterEncoderType> maybeParameterEncoderType) {
        return type.match(new ArgumentType.Cases<>() {
            @Override
            public CodeBlock primitive(TypeName _typeName, String parameterSerializerMethodName) {
                return maybeParameterEncoderType.map(this::parameterEncoderType).orElseGet(() -> {
                    return CodeBlock.of(
                            "$L.$L($S, $L.$L($L));",
                            REQUEST,
                            singleValueMethod,
                            key,
                            PARAMETER_SERIALIZER,
                            parameterSerializerMethodName,
                            argName);
                });
            }

            @Override
            public CodeBlock list(TypeName _typeName, ListType listType) {
                return maybeParameterEncoderType.map(this::parameterEncoderType).orElseGet(() -> {
                    CodeBlock elementName = CodeBlock.of(argName + "Element");
                    CodeBlock elementCodeBlock = generatePlainSerializer(
                            singleValueMethod,
                            multiValueMethod,
                            key,
                            elementName,
                            listType.innerType(),
                            Optional.empty());
                    return CodeBlock.of("$L.forEach($L -> { $L });", argName, elementName, elementCodeBlock);
                });
            }

            @Override
            public CodeBlock alias(TypeName _typeName, String parameterSerializerMethodName) {
                return maybeParameterEncoderType.map(this::parameterEncoderType).orElseGet(() -> {
                    return CodeBlock.of(
                            "$L.$L($S, $L.$L($L.get()));",
                            REQUEST,
                            singleValueMethod,
                            key,
                            PARAMETER_SERIALIZER,
                            parameterSerializerMethodName,
                            argName);
                });
            }

            @Override
            public CodeBlock optional(TypeName _typeName, OptionalType optionalType) {
                CodeBlock inner = generatePlainSerializer(
                        singleValueMethod,
                        multiValueMethod,
                        key,
                        CodeBlock.of("$L.$L()", argName, optionalType.valueGetMethodName()),
                        optionalType.innerType(),
                        maybeParameterEncoderType);
                return CodeBlock.builder()
                        .beginControlFlow("if ($L.$L())", argName, optionalType.isPresentMethodName())
                        .add(inner)
                        .endControlFlow()
                        .build();
            }

            @Override
            public CodeBlock enumType(TypeName _typeName, String parameterSerializerMethodName) {
                return maybeParameterEncoderType.map(this::parameterEncoderType).orElseGet(() -> {
                    return CodeBlock.of(
                            "$L.$L($S, $L.$L($L.toString()));",
                            REQUEST,
                            singleValueMethod,
                            key,
                            PARAMETER_SERIALIZER,
                            parameterSerializerMethodName,
                            argName);
                });
            }

            @Override
            public CodeBlock rawRequestBody(TypeName _typeName) {
                throw new UnsupportedOperationException("This should not happen");
            }

            @Override
            public CodeBlock customType(TypeName typeName) {
                ParameterEncoderType parameterEncoderType =
                        maybeParameterEncoderType.orElseThrow(() -> new IllegalArgumentException(
                                "Parameter '" + key + "' with custom type '" + typeName + "' must declare an encoder"));
                return parameterEncoderType(parameterEncoderType);
            }

            private CodeBlock parameterEncoderType(ParameterEncoderType parameterEncoderType) {
                return parameterEncoderType.type().match(new EncoderType.Cases<>() {
                    @Override
                    public CodeBlock param() {
                        return CodeBlock.of(
                                "$L.$L($S, $L.$L($L));",
                                REQUEST,
                                singleValueMethod,
                                key,
                                parameterEncoderType.encoderFieldName(),
                                parameterEncoderType.encoderMethodName(),
                                argName);
                    }

                    @Override
                    public CodeBlock listParam() {
                        return CodeBlock.of(
                                "$L.$L($S, $L.$L($L));",
                                REQUEST,
                                multiValueMethod,
                                key,
                                parameterEncoderType.encoderFieldName(),
                                parameterEncoderType.encoderMethodName(),
                                argName);
                    }

                    @Override
                    public CodeBlock multimapParam() {
                        return CodeBlock.of(
                                "$L.$L($L.$L($L));",
                                REQUEST,
                                multiValueMethod,
                                parameterEncoderType.encoderFieldName(),
                                parameterEncoderType.encoderMethodName(),
                                argName);
                    }
                });
            }
        });
    }
}
