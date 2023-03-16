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
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.annotations.Request;
import com.palantir.dialogue.annotations.processor.ErrorContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class EndpointDefinitions {

    private static final Function<ParameterType, Boolean> IS_PATH_PARAMETER =
            ParameterTypes.cases().path_(true).otherwise_(false);

    private final ParamTypesResolver paramTypesResolver;
    private final HttpPathParser httpPathParser;
    private final ArgumentTypesResolver argumentTypesResolver;
    private final ReturnTypesResolver returnTypesResolver;
    private final ErrorContext errorContext;

    public EndpointDefinitions(ErrorContext errorContext, Elements elements, Types types) {
        ResolverContext context = new ResolverContext(errorContext, elements, types);
        this.paramTypesResolver = new ParamTypesResolver(context);
        this.httpPathParser = new HttpPathParser(context);
        this.argumentTypesResolver = new ArgumentTypesResolver(context);
        this.returnTypesResolver = new ReturnTypesResolver(context);
        this.errorContext = errorContext;
    }

    public Optional<EndpointDefinition> tryParseEndpointDefinition(ExecutableElement element) {
        AnnotationReflector requestAnnotationReflector = MoreElements.getAnnotationMirror(element, Request.class)
                .toJavaUtil()
                .map(ImmutableAnnotationReflector::of)
                .orElseThrow();

        EndpointName endpointName =
                ImmutableEndpointName.of(element.getSimpleName().toString());

        HttpMethod method = HttpMethod.valueOf(requestAnnotationReflector
                .getFieldMaybe("method", VariableElement.class)
                .get()
                .getSimpleName()
                .toString());
        Optional<HttpPath> httpPath = httpPathParser.getHttpPath(element, requestAnnotationReflector);
        Optional<ReturnType> returnType =
                returnTypesResolver.getReturnType(endpointName, element, requestAnnotationReflector);
        List<ArgumentDefinition> argumentDefinitions = element.getParameters().stream()
                .map(arg -> getArgumentDefinition(endpointName, arg))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        if (httpPath.isEmpty()
                || returnType.isEmpty()
                || argumentDefinitions.size() != element.getParameters().size()) {
            return Optional.empty();
        }

        Set<String> expectedPathParams = httpPath.get().get().stream()
                .map(HttpPathSegments::getVariableName)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        Set<String> actualPathParams = argumentDefinitions.stream()
                .filter(argument -> IS_PATH_PARAMETER.apply(argument.paramType()))
                .map(argument -> argument.argName().get())
                .collect(Collectors.toSet());
        if (!actualPathParams.equals(expectedPathParams)) {
            errorContext.reportError("Path template parameters do not match method path parameters", element);
            return Optional.empty();
        }

        // TODO(12345): More validations around repeats etc.

        return Optional.of(ImmutableEndpointDefinition.builder()
                .endpointName(endpointName)
                .httpMethod(method)
                .httpPath(httpPath.get())
                .returns(returnType.get())
                .addAllArguments(argumentDefinitions)
                .build());
    }

    private Optional<ArgumentDefinition> getArgumentDefinition(EndpointName endpointName, VariableElement param) {
        ArgumentType argumentType = argumentTypesResolver.getArgumentType(param);
        Optional<ParameterType> parameterType = paramTypesResolver.getParameterType(endpointName, param);

        if (parameterType.isEmpty()) {
            return Optional.empty();
        }

        // TODO(12345): More validation around ArgumentType and ParameterType actually agreeing, e.g. if
        //  ArgumentType#requestBody then ParameterType#rawBody.

        return Optional.of(ImmutableArgumentDefinition.builder()
                .argName(ImmutableArgumentName.of(param.getSimpleName().toString()))
                .argType(argumentType)
                .paramType(parameterType.get())
                .build());
    }
}
