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

import com.google.common.base.Predicates;
import com.palantir.dialogue.annotations.Request;
import com.palantir.dialogue.annotations.processor.ArgumentType;
import com.palantir.dialogue.annotations.processor.ErrorContext;
import com.palantir.logsafe.Preconditions;
import com.squareup.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class EndpointDefinitions {

    private final ParamTypesResolver paramTypesResolver;
    private final HttpPathParser httpPathParser;
    private final ArgumentTypesResolver argumentTypesResolver;

    public EndpointDefinitions(ErrorContext errorContext, Elements elements, Types types) {
        this.paramTypesResolver = new ParamTypesResolver(errorContext, elements, types);
        this.httpPathParser = new HttpPathParser(errorContext);
        this.argumentTypesResolver = new ArgumentTypesResolver(errorContext, elements, types);
    }

    public Optional<EndpointDefinition> tryParseEndpointDefinition(ExecutableElement element) {
        Request requestAnnotation = Preconditions.checkNotNull(element.getAnnotation(Request.class), "No annotation");

        Optional<HttpPath> maybeHttpPath = httpPathParser.getHttpPath(element, requestAnnotation);
        List<Optional<ArgumentDefinition>> args = element.getParameters().stream()
                .map(this::getArgumentDefinition)
                .collect(Collectors.toList());

        if (!args.stream()
                        .filter(Predicates.not(Optional::isPresent))
                        .collect(Collectors.toList())
                        .isEmpty()
                || maybeHttpPath.isEmpty()) {
            return Optional.empty();
        }

        // TODO(12345): More validations around repeats etc.

        return Optional.of(ImmutableEndpointDefinition.builder()
                .endpointName(ImmutableEndpointName.of(element.getSimpleName().toString()))
                .httpMethod(requestAnnotation.method())
                .httpPath(maybeHttpPath.get())
                .returns(TypeName.get(element.getReturnType()))
                .addAllArguments(args.stream().map(Optional::get).collect(Collectors.toList()))
                .build());
    }

    private Optional<ArgumentDefinition> getArgumentDefinition(VariableElement param) {
        Optional<ArgumentType> argumentType = argumentTypesResolver.getArgumentType(param);
        Optional<ParameterType> parameterType = paramTypesResolver.getParameterType(param);

        if (argumentType.isEmpty() || parameterType.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ImmutableArgumentDefinition.builder()
                .argName(ImmutableArgumentName.of(param.getSimpleName().toString()))
                .argType(argumentType.get())
                .paramType(parameterType.get())
                .build());
    }
}
