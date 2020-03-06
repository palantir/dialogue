/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.dialogue.serde;

import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class TypeMarkers {

    static boolean isOptional(TypeMarker<?> marker) {
        Type type = marker.getType();
        if (OptionalDouble.class.equals(type) || OptionalInt.class.equals(type) || OptionalLong.class.equals(type)) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            return Optional.class.equals(parameterizedType.getRawType());
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static <T> T getEmptyOptional(TypeMarker<T> marker) {
        Type type = marker.getType();
        if (type instanceof ParameterizedType && Optional.class.equals(((ParameterizedType) type).getRawType())) {
            return (T) Optional.empty();
        } else if (OptionalDouble.class.equals(type)) {
            return (T) OptionalDouble.empty();
        } else if (OptionalInt.class.equals(type)) {
            return (T) OptionalInt.empty();
        } else if (OptionalLong.class.equals(type)) {
            return (T) OptionalLong.empty();
        }
        throw new SafeIllegalArgumentException(
                "Expected a TypeMarker representing an optional type", SafeArg.of("marker", marker));
    }

    private TypeMarkers() {}
}
