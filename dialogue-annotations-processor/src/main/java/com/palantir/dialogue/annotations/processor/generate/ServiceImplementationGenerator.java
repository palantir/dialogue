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

import com.palantir.dialogue.annotations.processor.data.EndpointDefinition;
import com.palantir.dialogue.annotations.processor.data.ServiceDefinition;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;

public final class ServiceImplementationGenerator {

    private final ServiceDefinition serviceDefinition;

    public ServiceImplementationGenerator(ServiceDefinition serviceDefinition) {
        this.serviceDefinition = serviceDefinition;
    }

    public TypeSpec generate() {
        TypeSpec.Builder impl =
                TypeSpec.anonymousClassBuilder("").addSuperinterface(serviceDefinition.serviceInterface());

        serviceDefinition.endpoints().forEach(endpoint -> {
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

        methodBuilder.returns(def.returns());

        methodBuilder.addCode("throw new UnsupportedOperationException();");

        return methodBuilder.build();
    }
}
