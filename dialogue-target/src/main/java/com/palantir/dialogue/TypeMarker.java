/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue;

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Captures generic type information.
 *
 * Usage example: <pre>new TypeMarker&lt;List&lt;Integer&gt;() {}</pre>.
 */
@SuppressWarnings("unused") // Generic type exists for compile time safety but is not used internally.
public abstract class TypeMarker<T> {

    private final Type type;

    protected TypeMarker() {
        Type genericSuperclass = getClass().getGenericSuperclass();
        if (!(genericSuperclass instanceof ParameterizedType)) {
            throw new SafeIllegalArgumentException(
                    "Class is not parameterized", SafeArg.of("class", genericSuperclass));
        }
        type = ((ParameterizedType) genericSuperclass).getActualTypeArguments()[0];
        if (type instanceof TypeVariable) {
            throw new SafeIllegalArgumentException(
                    "TypeMarker does not support variable types", SafeArg.of("typeVariable", type));
        }
    }

    public final Type getType() {
        return type;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof TypeMarker) {
            TypeMarker<?> that = (TypeMarker<?>) other;
            return type.equals(that.type);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return type.hashCode();
    }

    @Override
    public final String toString() {
        return "TypeMarker{type=" + type + '}';
    }
}
