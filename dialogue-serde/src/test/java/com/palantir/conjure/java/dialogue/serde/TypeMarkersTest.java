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

import static com.palantir.logsafe.testing.Assertions.assertThatLoggableExceptionThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.Test;

public class TypeMarkersTest {

    @Test
    public void testIsOptional_optional() {
        assertThat(TypeMarkers.isOptional(new TypeMarker<Optional<String>>() {}))
                .isTrue();
    }

    @Test
    public void testIsOptional_optionalDouble() {
        assertThat(TypeMarkers.isOptional(new TypeMarker<OptionalDouble>() {})).isTrue();
    }

    @Test
    public void testIsOptional_optionalLong() {
        assertThat(TypeMarkers.isOptional(new TypeMarker<OptionalLong>() {})).isTrue();
    }

    @Test
    public void testIsOptional_optionalInt() {
        assertThat(TypeMarkers.isOptional(new TypeMarker<OptionalInt>() {})).isTrue();
    }

    @Test
    public void testIsOptional_object() {
        assertThat(TypeMarkers.isOptional(new TypeMarker<Object>() {})).isFalse();
    }

    @Test
    public void testIsOptional_listOptional() {
        assertThat(TypeMarkers.isOptional(new TypeMarker<List<Optional<String>>>() {}))
                .isFalse();
    }

    @Test
    public void testGetEmptyOptional_optional() {
        assertThat(TypeMarkers.getEmptyOptional(new TypeMarker<Optional<String>>() {}))
                .isEmpty();
    }

    @Test
    public void testGetEmptyOptional_optionalDouble() {
        assertThat(TypeMarkers.getEmptyOptional(new TypeMarker<OptionalDouble>() {}))
                .isEmpty();
    }

    @Test
    public void testGetEmptyOptional_optionalLong() {
        assertThat(TypeMarkers.getEmptyOptional(new TypeMarker<OptionalLong>() {}))
                .isEmpty();
    }

    @Test
    public void testGetEmptyOptional_optionalInt() {
        assertThat(TypeMarkers.getEmptyOptional(new TypeMarker<OptionalInt>() {}))
                .isEmpty();
    }

    @Test
    public void testGetEmptyOptional_notOptionalType() {
        assertThatLoggableExceptionThrownBy(() -> TypeMarkers.getEmptyOptional(new TypeMarker<String>() {}))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasLogMessage("Expected a TypeMarker representing an optional type");
    }
}
