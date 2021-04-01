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

import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.annotations.processor.ArgumentType;
import com.palantir.dialogue.annotations.processor.ArgumentType.OptionalType;
import com.palantir.dialogue.annotations.processor.ArgumentTypes;
import com.palantir.dialogue.annotations.processor.data.ArgumentDefinition;
import com.palantir.dialogue.annotations.processor.data.EndpointDefinition;
import com.palantir.dialogue.annotations.processor.data.ParameterType.Cases;
import com.palantir.dialogue.annotations.processor.data.ParameterTypes;
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
    private static final String PLAIN_SER_DE = "_plainSerDe";

    private final ServiceDefinition serviceDefinition;

    public ServiceImplementationGenerator(ServiceDefinition serviceDefinition) {
        this.serviceDefinition = serviceDefinition;
    }

    public TypeSpec generate() {
        TypeSpec.Builder impl =
                TypeSpec.anonymousClassBuilder("").addSuperinterface(serviceDefinition.serviceInterface());

        impl.addField(FieldSpec.builder(PlainSerDe.class, PLAIN_SER_DE)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .initializer(CodeBlock.of("$L.plainSerDe()", serviceDefinition.conjureRuntimeArgName()))
                .build());

        serviceDefinition.endpoints().forEach(endpoint -> {
            endpoint.arguments().stream()
                    .flatMap(arg -> ParameterTypes.caseOf(arg.paramType())
                            .body((serializer, serializerFieldName) -> serializer(arg, serializer, serializerFieldName))
                            .otherwiseEmpty()
                            .stream())
                    .findAny()
                    .ifPresent(impl::addField);
            impl.addField(bindEndpointChannel(endpoint));
            impl.addMethod(clientImpl(endpoint));
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

        methodBuilder.returns(def.returns());

        methodBuilder.addCode("throw new $T();", UnsupportedOperationException.class);

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

    private Optional<CodeBlock> generateParam(ArgumentDefinition param) {
        return param.paramType().match(new Cases<>() {
            @Override
            public Optional<CodeBlock> rawBody() {
                return Optional.of(
                        CodeBlock.of("$L.body($L);", REQUEST, param.argName().get()));
            }

            @Override
            public Optional<CodeBlock> body(TypeName _unused, String serializerFieldName) {
                return Optional.of(CodeBlock.of(
                        "$L.body($L.serialize($L));",
                        REQUEST,
                        serializerFieldName,
                        param.argName().get()));
            }

            @Override
            public Optional<CodeBlock> header(String headerName) {
                return generateHeaderParam(param, headerName);
            }

            @Override
            public Optional<CodeBlock> path() {
                return generatePathParam(param);
            }

            @Override
            public Optional<CodeBlock> query(String paramName) {
                return generateQueryParam(param, paramName);
            }
        });
    }

    private Optional<CodeBlock> generateHeaderParam(ArgumentDefinition param, String headerName) {
        return generatePlainSerializer(
                "putHeaderParams", headerName, CodeBlock.of(param.argName().get()), param.argType());
    }

    private Optional<CodeBlock> generatePathParam(ArgumentDefinition param) {
        return generatePlainSerializer(
                "putPathParams",
                param.argName().get(),
                CodeBlock.of("$L", param.argName().get()),
                param.argType());
    }

    private Optional<CodeBlock> generateQueryParam(ArgumentDefinition param, String paramName) {
        return generatePlainSerializer(
                "putQueryParams", paramName, CodeBlock.of(param.argName().get()), param.argType());
    }

    private Optional<CodeBlock> generatePlainSerializer(
            String method, String key, CodeBlock argName, ArgumentType type) {
        return type.match(new ArgumentType.Cases<>() {
            @Override
            public Optional<CodeBlock> primitive(TypeName _unused, String plainSerDeMethodName) {
                return Optional.of(CodeBlock.of(
                        "$L.$L($S, $L.$L($L));", REQUEST, method, key, PLAIN_SER_DE, plainSerDeMethodName, argName));
            }

            @Override
            public Optional<CodeBlock> rawRequestBody(TypeName _unused) {
                throw new UnsupportedOperationException("This should not happen");
            }

            @Override
            public Optional<CodeBlock> optional(TypeName _unused, OptionalType optionalType) {
                return generatePlainSerializer(
                                method,
                                key,
                                CodeBlock.of("$L.$L()", argName, optionalType.valueGetMethodName()),
                                optionalType.underlyingType())
                        .map(inner -> CodeBlock.builder()
                                .beginControlFlow("if ($L.$L())", argName, optionalType.isPresentMethodName())
                                .add(inner)
                                .endControlFlow()
                                .build());
            }

            @Override
            public Optional<CodeBlock> customType(TypeName _unused) {
                return Optional.empty();
            }
        });
    }
}
