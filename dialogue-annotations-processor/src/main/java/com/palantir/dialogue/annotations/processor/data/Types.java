/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.google.auto.common.MoreTypes;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public final class Types {
    private Types() {}

    /**
     * Finds a public or protected constructor that accepts a single {@link ConjureRuntime} parameter.
     * <p>
     * criteria:
     * <ul>
     *   <li>The ctor must not be private</li>
     *   <li>The ctor must have exactly one parameter of type {@link ConjureRuntime}</li>
     * </ul>
     * <p>
     * If no matching constructor is found, the caller should fall back to using a no-argument constructor.
     */
    public static Optional<ExecutableElement> findConstructorWithConjureRuntimeParameter(
            ResolverContext context, TypeMirror typeMirror) {
        DeclaredType declaredType = MoreTypes.asDeclared(typeMirror);
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        return typeElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .filter(element -> !element.getModifiers().contains(Modifier.PRIVATE)
                        && element.getParameters().size() == 1
                        && element.getThrownTypes().stream().allMatch(t -> isRuntimeException(context, t))
                        && TypeName.get(element.getParameters().get(0).asType())
                                .equals(ClassName.get(ConjureRuntime.class)))
                .findAny();
    }

    private static boolean isRuntimeException(ResolverContext context, TypeMirror typeMirror) {
        return context.isAssignable(typeMirror, RuntimeException.class);
    }
}
