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

import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.DialogueServiceFactory;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.annotations.processor.data.ServiceDefinition;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

public final class DialogueServiceFactoryGenerator {

    private final ServiceDefinition serviceDefinition;
    private final String className;
    private final ServiceImplementationGenerator serviceImplementationGenerator;

    public DialogueServiceFactoryGenerator(ServiceDefinition serviceDefinition) {
        this.serviceDefinition = serviceDefinition;
        className = serviceDefinition.serviceInterface().simpleName() + "DialogueServiceFactory";
        this.serviceImplementationGenerator = new ServiceImplementationGenerator(serviceDefinition);
    }

    public TypeSpec generate() {

        TypeSpec.Builder method = TypeSpec.classBuilder(className)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("javax.annotation", "Generated"))
                        .addMember("value", "$S", getClass().getCanonicalName())
                        .build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get(DialogueServiceFactory.class), serviceDefinition.serviceInterface()));

        TypeSpec serviceImplementation = serviceImplementationGenerator.generate();

        method.addMethod(MethodSpec.methodBuilder("create")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(serviceDefinition.serviceInterface())
                .addParameter(EndpointChannelFactory.class, "endpointChannelFactory")
                .addParameter(ConjureRuntime.class, "runtime")
                .addCode(CodeBlock.builder()
                        .add("return $L;", serviceImplementation)
                        .build())
                .build());

        return method.build();
    }
}
