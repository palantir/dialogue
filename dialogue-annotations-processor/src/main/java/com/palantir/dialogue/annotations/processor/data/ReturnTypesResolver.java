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

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.annotations.processor.ErrorContext;
import com.squareup.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public final class ReturnTypesResolver {

    @SuppressWarnings("StrictUnusedVariable")
    private final ErrorContext errorContext;

    @SuppressWarnings("StrictUnusedVariable")
    private final Types types;

    private final TypeElement genericListenableFutureType;

    public ReturnTypesResolver(ErrorContext errorContext, Elements elements, Types types) {
        this.errorContext = errorContext;
        this.types = types;
        this.genericListenableFutureType = elements.getTypeElement(ListenableFuture.class.getCanonicalName());
    }

    public Optional<ReturnType> getReturnType(AnnotationReflector requestAnnotation, TypeMirror returnType) {
        Optional<TypeName> customDeserializer =
                requestAnnotation.getFieldMaybe("accepts", TypeMirror.class).map(TypeName::get);
        Optional<TypeName> maybeListenableFutureInnerType =
                getListenableFutureInnerType(returnType).map(TypeName::get);
        return Optional.of(ImmutableReturnType.builder()
                .returnType(TypeName.get(returnType))
                .isAsync(maybeListenableFutureInnerType)
                .customDeserializer(customDeserializer)
                .build());
    }

    private Optional<TypeMirror> getListenableFutureInnerType(TypeMirror typeName) {
        // "They tryin'a make me use a visitor, but I say: NO, NO NO!"
        if (!(typeName instanceof DeclaredType)) {
            return Optional.empty();
        }
        DeclaredType declaredType = (DeclaredType) typeName;
        if (declaredType.getTypeArguments().size() != 1) {
            return Optional.empty();
        }

        TypeMirror innerType = Iterables.getOnlyElement(declaredType.getTypeArguments());
        DeclaredType genericOptional = types.getDeclaredType(genericListenableFutureType, innerType);

        if (types.isSameType(genericOptional, declaredType)) {
            return Optional.of(innerType);
        } else {
            return Optional.empty();
        }
    }
}
