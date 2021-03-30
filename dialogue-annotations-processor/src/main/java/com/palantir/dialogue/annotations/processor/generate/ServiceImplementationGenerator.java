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

import com.google.common.collect.ImmutableMap;
import com.palantir.common.streams.KeyedStream;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.annotations.processor.data.ArgumentDefinition;
import com.palantir.dialogue.annotations.processor.data.EndpointDefinition;
import com.palantir.dialogue.annotations.processor.data.ParameterType.Cases;
import com.palantir.dialogue.annotations.processor.data.ServiceDefinition;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

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
            impl.addField(bindEndpointChannel(endpoint));
            impl.addMethod(clientImpl(endpoint));
        });

        return impl.build();
    }

    private MethodSpec clientImpl(EndpointDefinition def) {
        List<ParameterSpec> params = def.arguments().stream()
                .map(arg ->
                        ParameterSpec.builder(arg.type(), arg.argName().get()).build())
                .collect(Collectors.toList());

        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(
                        def.endpointName().get())
                .addModifiers(Modifier.PUBLIC)
                .addParameters(params)
                .addAnnotation(Override.class);

        methodBuilder.addCode("$T $L = $T.builder();", Request.Builder.class, REQUEST, Request.class);

        def.arguments().forEach(arg -> {
            CodeBlock codeBlock = generateParam(def.endpointName().get(), arg);
            if (codeBlock != null) {
                methodBuilder.addCode(codeBlock);
            }
        });

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

    private CodeBlock generateParam(String endpointName, ArgumentDefinition param) {
        return param.paramType().match(new Cases<CodeBlock>() {
            @Override
            public CodeBlock rawBody() {
                return null;
            }

            @Override
            public CodeBlock body(Optional<TypeMirror> serializer) {
                return null;
            }

            @Override
            public CodeBlock header(String headerName) {
                return generateHeaderParam(param, headerName);
            }

            @Override
            public CodeBlock path() {
                return generatePathParam(param);
            }

            @Override
            public CodeBlock query(String paramName) {
                return generateQueryParam(param, paramName);
            }

            // @Override
            // public CodeBlock visitBody(BodyParameterType value) {
            //     if (parameterTypes
            //             .baseType(param.getType())
            //             .equals(parameterTypes.baseType(Type.primitive(PrimitiveType.BINARY)))) {
            //         return CodeBlock.of(
            //                 "$L.body($L.bodySerDe().serialize($L));",
            //                 REQUEST,
            //                 StaticFactoryMethodGenerator.RUNTIME,
            //                 param.getArgName());
            //     }
            //     return CodeBlock.of("$L.body($LSerializer.serialize($L));", REQUEST, endpointName,
            // param.getArgName());
            // }
        });
    }

    private CodeBlock generateHeaderParam(ArgumentDefinition param, String headerName) {
        return generatePlainSerializer(
                "putHeaderParams", headerName, CodeBlock.of(param.argName().get()), param.type());
    }

    private CodeBlock generatePathParam(ArgumentDefinition param) {
        return generatePlainSerializer(
                "putPathParams",
                param.argName().get(),
                CodeBlock.of("$L", param.argName().get()),
                param.type());
    }

    private CodeBlock generateQueryParam(ArgumentDefinition param, String paramName) {
        return generatePlainSerializer(
                "putQueryParams", paramName, CodeBlock.of(param.argName().get()), param.type());
    }

    private CodeBlock generatePlainSerializer(String method, String key, CodeBlock argName, TypeName type) {
        if (isPrimitive(type)) {
            return CodeBlock.of(
                    "$L.$L($S, $L.serialize$L($L));",
                    REQUEST,
                    method,
                    key,
                    PLAIN_SER_DE,
                    primitiveTypeName(type),
                    argName);
        }
        return null;
        // return type.accept(new Type.Visitor<CodeBlock>() {
        //     @Override
        //     public CodeBlock visitPrimitive(PrimitiveType primitiveType) {
        //         return CodeBlock.of(
        //                 "$L.$L($S, $L.serialize$L($L));",
        //                 "_request",
        //                 method,
        //                 key,
        //                 PLAIN_SER_DE,
        //                 primitiveTypeName(primitiveType),
        //                 argName);
        //     }
        //
        //     @Override
        //     public CodeBlock visitOptional(OptionalType optionalType) {
        //
        //         return CodeBlock.builder()
        //                 .beginControlFlow("if ($L.isPresent())", argName)
        //                 .add(generatePlainSerializer(
        //                         method,
        //                         key,
        //                         CodeBlock.of("$L.$L()", argName, getOptionalAccessor(optionalType.getItemType())),
        //                         optionalType.getItemType()))
        //                 .endControlFlow()
        //                 .build();
        //     }
        //
        //     @Override
        //     public CodeBlock visitList(ListType value) {
        //         return visitCollection(value.getItemType());
        //     }
        //
        //     @Override
        //     public CodeBlock visitSet(SetType value) {
        //         return visitCollection(value.getItemType());
        //     }
        //
        //     @Override
        //     public CodeBlock visitMap(MapType value) {
        //         throw new SafeIllegalStateException("Maps can not be query parameters");
        //     }
        //
        //     @Override
        //     public CodeBlock visitReference(com.palantir.conjure.spec.TypeName typeName) {
        //         TypeDefinition typeDef = typeNameResolver.resolve(typeName);
        //         if (typeDef.accept(TypeDefinitionVisitor.IS_ALIAS)) {
        //             return generatePlainSerializer(
        //                     method,
        //                     key,
        //                     CodeBlock.of("$L.get()", argName),
        //                     typeDef.accept(TypeDefinitionVisitor.ALIAS).getAlias());
        //         } else if (typeDef.accept(TypeDefinitionVisitor.IS_ENUM)) {
        //             return CodeBlock.of("$L.$L($S, $T.toString($L));", "_request", method, key, Objects.class,
        // argName);
        //         }
        //         throw new IllegalStateException("Plain serialization can only be aliases and enums");
        //     }
        //
        //     @Override
        //     public CodeBlock visitExternal(ExternalReference value) {
        //         // TODO(forozco): we could probably do something smarter than just calling toString
        //         return CodeBlock.of("$L.$L($S, $T.toString($L));", "_request", method, key, Objects.class, argName);
        //     }
        //
        //     @Override
        //     public CodeBlock visitUnknown(String unknownType) {
        //         throw new SafeIllegalStateException("Unknown param type", SafeArg.of("type", unknownType));
        //     }
        //
        //     private CodeBlock visitCollection(Type itemType) {
        //         CodeBlock elementVariable = CodeBlock.of("$LElement", argName);
        //         return CodeBlock.builder()
        //                 .beginControlFlow(
        //                         "for ($T $L : $L)", parameterTypes.baseType(itemType), elementVariable, argName)
        //                 .add(generatePlainSerializer(method, key, elementVariable, itemType))
        //                 .endControlFlow()
        //                 .build();
        //     }
        // });
    }

    // private static String getOptionalAccessor(Type type) {
    //     if (type.accept(TypeVisitor.IS_PRIMITIVE)) {
    //         PrimitiveType primitive = type.accept(TypeVisitor.PRIMITIVE);
    //         if (primitive.equals(PrimitiveType.DOUBLE)) {
    //             return "getAsDouble";
    //         } else if (primitive.equals(PrimitiveType.INTEGER)) {
    //             return "getAsInt";
    //         }
    //     }
    //     return "get";
    // }

    private static final ImmutableMap<TypeName, String> PRIMITIVE_TO_TYPE_NAME =
            ImmutableMap.copyOf(KeyedStream.stream(new ImmutableMap.Builder<Class<?>, String>()
                            // .put(PrimitiveType.Value.BEARERTOKEN, "BearerToken")
                            // .put(PrimitiveType.Value.BOOLEAN, "Boolean")
                            // .put(PrimitiveType.Value.DATETIME, "DateTime")
                            // .put(PrimitiveType.Value.DOUBLE, "Double")
                            // .put(PrimitiveType.Value.INTEGER, "Integer")
                            // .put(PrimitiveType.Value.RID, "Rid")
                            // .put(PrimitiveType.Value.SAFELONG, "SafeLong")
                            // .put(PrimitiveType.Value.STRING, "String")
                            .put(UUID.class, "Uuid")
                            .build())
                    .mapKeys((Function<Class<?>, ClassName>) ClassName::get)
                    .collectToMap());

    private static boolean isPrimitive(TypeName in) {
        return PRIMITIVE_TO_TYPE_NAME.containsKey(in);
    }

    private static String primitiveTypeName(TypeName in) {
        String typeName = PRIMITIVE_TO_TYPE_NAME.get(in);
        if (typeName == null) {
            throw new IllegalStateException("unrecognized primitive type: " + in);
        }
        return typeName;
    }
}
