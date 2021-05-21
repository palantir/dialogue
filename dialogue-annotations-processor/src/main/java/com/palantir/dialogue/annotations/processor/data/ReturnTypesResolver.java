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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.annotations.ConjureErrorDecoder;
import com.palantir.dialogue.annotations.Json;
import com.squareup.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

public final class ReturnTypesResolver {

    private final ResolverContext context;

    public ReturnTypesResolver(ResolverContext context) {
        this.context = context;
    }

    public Optional<ReturnType> getReturnType(
            EndpointName endpointName, ExecutableElement element, AnnotationReflector requestAnnotation) {
        TypeMirror returnType = element.getReturnType();

        Optional<TypeMirror> maybeListenableFutureInnerType = getListenableFutureInnerType(returnType);
        // TODO(12345): Validate deserializer types match
        Optional<TypeMirror> maybeAcceptDeserializerFactory =
                requestAnnotation.getFieldMaybe("accept", TypeMirror.class);
        Optional<TypeMirror> maybeErrorDecoder = requestAnnotation.getFieldMaybe("errorDecoder", TypeMirror.class);
        return Optional.of(ImmutableReturnType.builder()
                .returnType(TypeName.get(returnType))
                .deserializerFactory(maybeAcceptDeserializerFactory
                        .map(TypeName::get)
                        .orElseGet(() -> context.getTypeName(Json.class)))
                .errorDecoder(maybeErrorDecoder
                        .map(TypeName::get)
                        .orElseGet(() -> context.getTypeName(ConjureErrorDecoder.class)))
                .deserializerFieldName(InstanceVariables.joinCamelCase(endpointName.get(), "Deserializer"))
                .asyncInnerType(maybeListenableFutureInnerType.map(TypeName::get))
                .build());
    }

    private Optional<TypeMirror> getListenableFutureInnerType(TypeMirror typeName) {
        return context.maybeAsDeclaredType(typeName)
                .flatMap(declaredType -> context.getGenericInnerType(ListenableFuture.class, declaredType));
    }
}
