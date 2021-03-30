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
import com.palantir.dialogue.annotations.Request.Body;
import com.palantir.dialogue.annotations.Request.PathParam;
import com.palantir.logsafe.Preconditions;
import com.squareup.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import org.glassfish.jersey.uri.internal.UriTemplateParser;

public final class EndpointDefinitions {

    @SuppressWarnings("StrictUnusedVariable")
    private final ErrorContext errorContext;

    public EndpointDefinitions(ErrorContext errorContext) {
        this.errorContext = errorContext;
    }

    public Optional<EndpointDefinition> tryParseEndpointDefinition(ExecutableElement element) {
        Request requestAnnotation = Preconditions.checkNotNull(element.getAnnotation(Request.class), "No annotation");
        UriTemplateParser uriTemplateParser = new UriTemplateParser(requestAnnotation.path());

        List<Optional<ArgumentDefinition>> args = element.getParameters().stream()
                .map(this::getArgumentDefinition)
                .collect(Collectors.toList());

        if (!args.stream()
                .filter(Predicates.not(Optional::isPresent))
                .collect(Collectors.toList())
                .isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ImmutableEndpointDefinition.builder()
                .endpointName(ImmutableEndpointName.of(element.getSimpleName().toString()))
                .httpMethod(requestAnnotation.method())
                .httpPath(ImmutableHttpPath.of(uriTemplateParser.getNormalizedTemplate()))
                .returns(TypeName.get(element.getReturnType()))
                .addAllArguments(args.stream().map(Optional::get).collect(Collectors.toList()))
                .build());
    }

    private Optional<ArgumentDefinition> getArgumentDefinition(VariableElement param) {
        return Optional.of(ImmutableArgumentDefinition.builder()
                .argName(ImmutableArgumentName.of(param.getSimpleName().toString()))
                .type(TypeName.get(param.asType()))
                .paramType(getParameterType(param))
                .build());
    }

    private ParameterType getParameterType(VariableElement variableElement) {
        // This should be tightened:
        // 1. check that only single annotation is present etc.
        // 2. check that values are not empty/of the right shape etc.
        // 3. Rewrite using TypeMirrors:
        // https://hauchee.blogspot.com/2015/12/compile-time-annotation-processing-getting-class-value.html
        Body body = variableElement.getAnnotation(Body.class);
        if (body != null) {
            try {
                // Always going to throw
                return ParameterTypes.body((TypeMirror) (Object) body.value());
            } catch (MirroredTypeException e) {
                TypeMirror typeMirror = e.getTypeMirror();
                return ParameterTypes.body(typeMirror);
            }
        }

        Request.Header header = variableElement.getAnnotation(Request.Header.class);
        if (header != null) {
            return ParameterTypes.header(header.value());
        }

        PathParam pathParam = variableElement.getAnnotation(Request.PathParam.class);
        if (pathParam != null) {
            return ParameterTypes.path();
        }

        Request.QueryParam queryParam = variableElement.getAnnotation(Request.QueryParam.class);
        if (queryParam != null) {
            return ParameterTypes.query(queryParam.value());
        }

        // Check type is actually RequestBody;

        return ParameterTypes.rawBody();
    }
}
