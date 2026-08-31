/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TypeMarkerTest {

    @Test
    void createsSimpleTypeMarker() {
        TypeMarker<String> marker = TypeMarker.of(String.class);

        assertThat(marker).isEqualTo(new TypeMarker<String>() {});
        assertThat(marker.getType()).isEqualTo(String.class);
    }

    @Test
    void createsListTypeMarker() {
        assertEquivalent(TypeMarker.listOf(String.class), new TypeMarker<List<String>>() {});
    }

    @Test
    void createsSetTypeMarker() {
        assertEquivalent(TypeMarker.setOf(String.class), new TypeMarker<Set<String>>() {});
    }

    @Test
    void createsOptionalTypeMarker() {
        assertEquivalent(TypeMarker.optionalOf(String.class), new TypeMarker<Optional<String>>() {});
    }

    @Test
    void createsMapTypeMarker() {
        assertEquivalent(TypeMarker.mapOf(String.class, Integer.class), new TypeMarker<Map<String, Integer>>() {});
    }

    private static void assertEquivalent(TypeMarker<?> factoryMarker, TypeMarker<?> anonymousMarker) {
        assertThat(factoryMarker).isEqualTo(anonymousMarker);
        assertThat(anonymousMarker).isEqualTo(factoryMarker);
        assertThat(factoryMarker).hasSameHashCodeAs(anonymousMarker);
        assertThat(factoryMarker.getType().getTypeName())
                .isEqualTo(anonymousMarker.getType().getTypeName());
    }
}
