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
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.dialogue.annotations.processor.ErrorContext;
import com.palantir.logsafe.Arg;
import com.squareup.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor9;
import javax.lang.model.util.Types;

public final class ResolverContext implements ErrorContext {

    private final ErrorContext errorContext;
    private final Elements elements;
    private final Types types;

    public ResolverContext(ErrorContext errorContext, Elements elements, Types types) {
        this.errorContext = errorContext;
        this.elements = elements;
        this.types = types;
    }

    public boolean isSameTypes(TypeMirror typeMirror, Class<?> clazz) {
        return types.isSameType(typeMirror, getTypeMirror(clazz));
    }

    public boolean isAssignable(TypeMirror typeMirror, Class<?> clazz) {
        return types.isAssignable(typeMirror, getTypeMirror(clazz));
    }

    public TypeName getTypeName(Class<?> clazz) {
        return TypeName.get(getTypeMirror(clazz));
    }

    public TypeMirror getTypeMirror(Class<?> clazz) {
        return getTypeElement(clazz).asType();
    }

    public Optional<DeclaredType> maybeAsDeclaredType(TypeMirror typeName) {
        return typeName.accept(DeclaredTypeOptionalVisitor.INSTANCE, null);
    }

    public Optional<TypeMirror> getGenericInnerType(Class<?> clazz, TypeMirror typeMirror) {
        return maybeAsDeclaredType(typeMirror).flatMap(declaredType -> {
            if (declaredType.getTypeArguments().size() != 1) {
                return Optional.empty();
            }

            TypeMirror innerType = Iterables.getOnlyElement(declaredType.getTypeArguments());
            DeclaredType erasedType = types.getDeclaredType(getTypeElement(clazz), innerType);

            if (types.isAssignable(declaredType, erasedType)) {
                return Optional.of(innerType);
            } else {
                return Optional.empty();
            }
        });
    }

    @Override
    public void reportError(@CompileTimeConstant String message, Element element, Arg<?>... args) {
        errorContext.reportError(message, element, args);
    }

    @Override
    public void reportError(@CompileTimeConstant String message, Element element, Throwable throwable) {
        errorContext.reportError(message, element, throwable);
    }

    private TypeElement getTypeElement(Class<?> clazz) {
        return elements.getTypeElement(clazz.getCanonicalName());
    }

    private static final class DeclaredTypeOptionalVisitor extends OptionalTypeVisitor<DeclaredType> {
        private static final DeclaredTypeOptionalVisitor INSTANCE = new DeclaredTypeOptionalVisitor();

        @Override
        public Optional<DeclaredType> visitDeclared(DeclaredType declaredType, Void _unused) {
            return Optional.of(declaredType);
        }
    }

    private abstract static class OptionalTypeVisitor<T> extends SimpleTypeVisitor9<Optional<T>, Void> {
        OptionalTypeVisitor() {
            super(Optional.empty());
        }
    }
}
