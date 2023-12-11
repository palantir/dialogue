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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.MustBeClosed;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.annotations.ConjureErrorDecoder;
import com.palantir.dialogue.annotations.InputStreamDeserializer;
import com.palantir.dialogue.annotations.Json;
import com.palantir.dialogue.annotations.ResponseDeserializer;
import com.squareup.javapoet.TypeName;
import java.io.InputStream;
import java.util.Optional;
import javax.lang.model.element.Element;
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

        boolean hasMustBeClosed =
                MoreElements.getAnnotationMirror(element, MustBeClosed.class).isPresent();

        Optional<TypeMirror> maybeListenableFutureInnerType = getListenableFutureInnerType(returnType);
        // TODO(12345): Validate deserializer types match
        Optional<TypeMirror> maybeAcceptDeserializerFactory =
                requestAnnotation.getFieldMaybe("accept", TypeMirror.class);
        Optional<TypeMirror> maybeErrorDecoder = requestAnnotation.getFieldMaybe("errorDecoder", TypeMirror.class);

        Optional<TypeName> maybeDeserializerFactory = maybeAcceptDeserializerFactory
                .map(TypeName::get)
                .or(() -> orDefaultDeserializerFactory(
                        hasMustBeClosed, element, returnType, maybeListenableFutureInnerType));

        if (maybeDeserializerFactory.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ImmutableReturnType.builder()
                .returnType(TypeName.get(returnType))
                .deserializerFactory(maybeDeserializerFactory.get())
                .errorDecoder(maybeErrorDecoder
                        .map(TypeName::get)
                        .orElseGet(() -> context.getTypeName(ConjureErrorDecoder.class)))
                .deserializerFieldName(InstanceVariables.joinCamelCase(endpointName.get(), "Deserializer"))
                .asyncInnerType(maybeListenableFutureInnerType.map(TypeName::get))
                .build());
    }

    private Optional<TypeName> orDefaultDeserializerFactory(
            boolean hasMustBeClosed,
            Element element,
            TypeMirror returnType,
            Optional<TypeMirror> maybeListenableFutureInnerType) {
        boolean isReturnResponseType = isResponseType(returnType);
        if (isReturnResponseType
                || maybeListenableFutureInnerType.map(this::isResponseType).orElse(false)) {
            if (isReturnResponseType && !hasMustBeClosed) {
                context.reportError("When returning raw Response, remember to add @MustBeClosed annotation", element);
                return Optional.empty();
            }
            return Optional.of(context.getTypeName(ResponseDeserializer.class));
        } else if (isInputStreamType(returnType)) {
            if (!hasMustBeClosed) {
                context.reportError("When returning InputStream, remember to add @MustBeClosed annotation", element);
                return Optional.empty();
            }
            return Optional.of(context.getTypeName(InputStreamDeserializer.class));
        }
        return Optional.of(context.getTypeName(Json.class));
    }

    private boolean isResponseType(TypeMirror type) {
        return context.isSameTypes(type, Response.class);
    }

    private boolean isInputStreamType(TypeMirror type) {
        return context.isSameTypes(type, InputStream.class);
    }

    private Optional<TypeMirror> getListenableFutureInnerType(TypeMirror typeName) {
        return context.maybeAsDeclaredType(typeName)
                .flatMap(declaredType -> context.getGenericInnerType(ListenableFuture.class, declaredType));
    }
}
