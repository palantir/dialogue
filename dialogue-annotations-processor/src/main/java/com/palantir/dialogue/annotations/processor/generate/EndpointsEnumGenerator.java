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

import com.google.common.collect.ListMultimap;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.PathTemplate;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.dialogue.annotations.processor.data.EndpointDefinition;
import com.palantir.dialogue.annotations.processor.data.HttpPath;
import com.palantir.dialogue.annotations.processor.data.HttpPathSegment;
import com.palantir.dialogue.annotations.processor.data.HttpPathSegment.Cases;
import com.palantir.dialogue.annotations.processor.data.ServiceDefinition;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.Optional;
import javax.lang.model.element.Modifier;

public final class EndpointsEnumGenerator {

    private final ServiceDefinition serviceDefinition;

    public EndpointsEnumGenerator(ServiceDefinition serviceDefinition) {
        this.serviceDefinition = serviceDefinition;
    }

    TypeSpec generate() {
        TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(serviceDefinition.endpointsEnum())
                .addModifiers(Modifier.PRIVATE)
                .addSuperinterface(ClassName.get(Endpoint.class));

        serviceDefinition.endpoints().forEach(endpoint -> {
            enumBuilder.addEnumConstant(
                    endpoint.endpointName().get(),
                    endpointField(endpoint, serviceDefinition.serviceInterface().simpleName()));
        });

        enumBuilder.addField(FieldSpec.builder(
                        TypeName.get(String.class), "VERSION", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(
                        "$T.ofNullable($T.class.getPackage().getImplementationVersion()).orElse(\"0.0.0\")",
                        TypeName.get(Optional.class),
                        serviceDefinition.serviceInterface())
                .build());

        return enumBuilder.build();
    }

    private TypeSpec endpointField(EndpointDefinition def, String serviceName) {
        TypeSpec.Builder builder = TypeSpec.anonymousClassBuilder("");

        builder.addField(FieldSpec.builder(
                                TypeName.get(PathTemplate.class), "pathTemplate", Modifier.PRIVATE, Modifier.FINAL)
                        .initializer(pathTemplateInitializer(def.httpPath()))
                        .build())
                .addMethod(MethodSpec.methodBuilder("renderPath")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(
                                ParameterizedTypeName.get(ListMultimap.class, String.class, String.class), "params")
                        .addParameter(UrlBuilder.class, "url")
                        .addCode("pathTemplate.fill(params, url);")
                        .build())
                .addMethod(MethodSpec.methodBuilder("httpMethod")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(HttpMethod.class)
                        .addCode(CodeBlock.builder()
                                .add("return $T.$L;", HttpMethod.class, def.httpMethod())
                                .build())
                        .build())
                .addMethod(MethodSpec.methodBuilder("serviceName")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addCode(CodeBlock.builder()
                                .add("return $S;", serviceName)
                                .build())
                        .build())
                .addMethod(MethodSpec.methodBuilder("endpointName")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addCode(CodeBlock.builder()
                                .add("return $S;", def.endpointName().get())
                                .build())
                        .build())
                .addMethod(MethodSpec.methodBuilder("version")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addCode(CodeBlock.of("return VERSION;"))
                        .build());
        return builder.build();
    }

    private static CodeBlock pathTemplateInitializer(HttpPath path) {
        CodeBlock.Builder pathTemplateBuilder = CodeBlock.builder().add("$T.builder()", PathTemplate.class);

        for (HttpPathSegment segment : path.get()) {
            segment.match(new Cases<Void>() {
                @Override
                public Void fixed(String value) {
                    pathTemplateBuilder.add(".fixed($S)", value);
                    return null;
                }

                @Override
                public Void variable(String variableName) {
                    pathTemplateBuilder.add(".variable($S)", variableName);
                    return null;
                }
            });
        }

        CodeBlock build = pathTemplateBuilder.add(".build()").build();
        return build;
    }
}
