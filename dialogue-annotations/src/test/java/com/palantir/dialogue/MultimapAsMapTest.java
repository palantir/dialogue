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

package com.palantir.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultimapAsMapTest {

    @Test
    public void testSimpleConversion() {
        ImmutableListMultimap<String, String> multimap = ImmutableListMultimap.<String, String>builder()
                .put("a", "b")
                .put("c", "d")
                .build();
        assertThat(MultimapAsMap.of(multimap))
                .hasSize(2)
                .containsExactlyEntriesOf(ImmutableMap.of("a", "b", "c", "d"))
                .containsOnlyKeys("a", "c");
    }

    @Test
    public void testMultipleValues() {
        ImmutableListMultimap<String, String> multimap = ImmutableListMultimap.<String, String>builder()
                .put("a", "b")
                .putAll("c", "d", "e")
                .build();
        Map<String, String> map = MultimapAsMap.of(multimap);
        assertThat(map).hasSize(2);
        assertThat(map.keySet()).containsExactlyInAnyOrder("a", "c");
        assertThat(map.get("a")).isEqualTo("b");
        assertThatThrownBy(() -> map.get("c")).isInstanceOf(SafeIllegalStateException.class);
    }
}
