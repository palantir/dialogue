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

import com.google.auto.common.MoreTypes;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.dialogue.annotations.DefaultParameterSerializer;
import com.palantir.dialogue.annotations.ErrorHandlingDeserializerFactory;
import com.palantir.dialogue.annotations.ErrorHandlingVoidDeserializer;
import com.palantir.dialogue.annotations.InputStreamDeserializer;
import com.palantir.dialogue.annotations.Json;
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
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

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
                            .body((serializer, serializerTypeMirror, serializerFieldName) ->
                                    Optional.of(serializer(arg, serializer, serializerTypeMirror, serializerFieldName)))
                            .header((_headerName, maybeEncoder) ->
                                    maybeEncoder.map(ServiceImplementationGenerator::encoder))
                            .headerMap(encoder -> Optional.of(encoder(encoder)))
                            .path(maybeEncoder -> maybeEncoder.map(ServiceImplementationGenerator::encoder))
                            .query((_paramName, maybeEncoder) ->
                                    maybeEncoder.map(ServiceImplementationGenerator::encoder))
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

        if (def.deprecated()) {
            methodBuilder.addAnnotation(AnnotationSpec.builder(Deprecated.class).build());
        }

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

    private static FieldSpec serializer(
            ArgumentDefinition argumentDefinition,
            TypeName serializerType,
            TypeMirror serializerTypeMirror,
            String serializerFieldName) {
        TypeName className = ArgumentTypes.caseOf(argumentDefinition.argType())
                .primitive((typeName, _parameterSerializerMethodName) -> typeName)
                .list((typeName, _parameterSerializerMethodName) -> typeName)
                .alias((typeName, _parameterSerializerMethodName) -> typeName)
                .customType(typeName -> typeName)
                .otherwiseEmpty()
                .orElseThrow(() -> new SafeIllegalStateException(
                        "Unsupported argument type for serializer", SafeArg.of("type", argumentDefinition.argType())));
        ParameterizedTypeName deserializerType = ParameterizedTypeName.get(ClassName.get(Serializer.class), className);

        CodeBlock realSerializer;
        // If it's JSON, just pass in the runtime.bodySerDe(). If it's custom, check if there's a ctor taking
        // Runtime and pass that in.
        if (serializerType.equals(ClassName.get(Json.class))) {
            realSerializer = CodeBlock.of(
                    "new $T(runtime.bodySerDe()).serializerFor(new $T<$T>() {})",
                    serializerType,
                    TypeMarker.class,
                    className);
        } else {
            // serializerType must point to a custom serializer
            // reflectively check if serializerType has a ctor that takes a ConjureRuntime
            realSerializer = findConstructorWithConjureRuntimeParameter(serializerTypeMirror)
                    .map(_element -> CodeBlock.of(
                            "new $T(runtime).serializerFor(new $T<$T>() {})",
                            serializerType,
                            TypeMarker.class,
                            className))
                    .orElseGet(() -> CodeBlock.of(
                            "new $T().serializerFor(new $T<$T>() {})", serializerType, TypeMarker.class, className));
        }
        return FieldSpec.builder(deserializerType, serializerFieldName)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                // TODO(pm): what are the possible values for SerializerType here? JSON and something custom?
                .initializer(realSerializer)
                .build();
    }

    private Optional<FieldSpec> deserializer(ReturnType type) {
        TypeName fullReturnType = type.returnType().box();
        TypeName deserializerFactoryType = type.deserializerFactory();
        TypeName errorDecoderType = type.errorDecoder();
        TypeName innerType = type.asyncInnerType().map(TypeName::box).orElse(fullReturnType);
        ParameterizedTypeName deserializerType =
                ParameterizedTypeName.get(ClassName.get(Deserializer.class), innerType);

        // When the return type deserializer is Json or InputStreamDeserializer, pass in runtime.BodySerDe. When it's
        // custom, maybe pass in runtime.

        CodeBlock deserializerFactoryWithArgs;
        if (deserializerFactoryType.equals(ClassName.get(Json.class))
                || deserializerFactoryType.equals(ClassName.get(InputStreamDeserializer.class))) {
            deserializerFactoryWithArgs = CodeBlock.of("$T(runtime.bodySerDe())", deserializerFactoryType);
        } else if (type.deserializerFactoryType().isEmpty()) {
            deserializerFactoryWithArgs = CodeBlock.of("$T()", deserializerFactoryType);
        } else {
            TypeMirror deserializerFactoryTypeMirror =
                    type.deserializerFactoryType().get();
            deserializerFactoryWithArgs = findConstructorWithConjureRuntimeParameter(deserializerFactoryTypeMirror)
                    .map(_element -> CodeBlock.of("$T(runtime)", deserializerFactoryType))
                    .orElseGet(() -> CodeBlock.of("$T()", deserializerFactoryType));
        }
        CodeBlock realDeserializer = CodeBlock.of(
                "new $T<>(new $L, new $T()).deserializerFor(new $T<$T>() {})",
                ErrorHandlingDeserializerFactory.class,
                deserializerFactoryWithArgs,
                errorDecoderType,
                TypeMarker.class,
                innerType);
        // TODO(pm): unrelated by why construct this if it's unused potentially.
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

    private static Optional<ExecutableElement> findConstructorWithConjureRuntimeParameter(TypeMirror typeMirror) {
        // TOOD(pm): nit: Should we also use Instantiables.instantiate here? It'd allow devs to write deserializers
        // as enum singletons.
        DeclaredType declaredType = MoreTypes.asDeclared(typeMirror);
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        return typeElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .filter(element -> !element.getModifiers().contains(Modifier.PRIVATE)
                        && element.getParameters().size() == 1
                        && TypeName.get(element.getParameters().get(0).asType())
                                .equals(ClassName.get(ConjureRuntime.class)))
                .findAny();
    }

    private static FieldSpec encoder(ParameterEncoderType type) {
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
            public CodeBlock body(
                    TypeName _serializerFactory, TypeMirror _serializerTypeMirror, String serializerFieldName) {
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
            public CodeBlock headerMap(ParameterEncoderType parameterEncoderType) {
                return generateHeaderMapParam(param, parameterEncoderType);
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

    private CodeBlock generateHeaderMapParam(ArgumentDefinition param, ParameterEncoderType paramEncoder) {
        return generatePlainSerializer(
                "nope",
                "putAllHeaderParams",
                param.argName().get(),
                CodeBlock.of("$L", param.argName().get()),
                param.argType(),
                Optional.of(paramEncoder));
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
                    CodeBlock asList = CodeBlock.of(
                            "$L.stream()$L.collect($T.toList())",
                            argName,
                            generateListElementSerializerCall(listType.innerType()),
                            Collectors.class);
                    return CodeBlock.builder()
                            .add("$L.$L($S,", REQUEST, multiValueMethod, key)
                            .add(asList)
                            .add(");")
                            .build();
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

    private CodeBlock generateListElementSerializerCall(ArgumentType type) {
        return type.match(new ArgumentType.Cases<>() {
            @Override
            public CodeBlock primitive(TypeName _typeName, String parameterSerializerMethodName) {
                return CodeBlock.of(".map($L::$L)", PARAMETER_SERIALIZER, parameterSerializerMethodName);
            }

            @Override
            public CodeBlock list(TypeName _typeName, ListType _listType) {
                throw new UnsupportedOperationException("This should not happen");
            }

            @Override
            public CodeBlock alias(TypeName typeName, String parameterSerializerMethodName) {
                return CodeBlock.of(
                        ".map($T::get).map($L::$L)", typeName, PARAMETER_SERIALIZER, parameterSerializerMethodName);
            }

            @Override
            public CodeBlock optional(TypeName _typeName, OptionalType _optionalType) {
                throw new UnsupportedOperationException("This should not happen");
            }

            @Override
            public CodeBlock rawRequestBody(TypeName _typeName) {
                throw new UnsupportedOperationException("This should not happen");
            }

            @Override
            public CodeBlock customType(TypeName _typeName) {
                throw new UnsupportedOperationException("This should not happen");
            }
        });
    }
}
