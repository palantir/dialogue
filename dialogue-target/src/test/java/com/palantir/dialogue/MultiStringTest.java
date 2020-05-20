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

package com.palantir.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

class MultiStringTest {
    @Test
    void decode() {
        assertThat(MultiString.decode("")).containsExactly("");
        assertThat(MultiString.decode("hello")).containsExactly("hello");
        assertThat(MultiString.decode("hello|world")).containsExactly("hello", "world");
        assertThat(MultiString.decode("hello|world|")).containsExactly("hello", "world", "");
        assertThat(MultiString.decode("hello^|world")).containsExactly("hello|world");
        assertThat(MultiString.decode("hello^^world")).containsExactly("hello^world");
        assertThat(MultiString.decode("hello^^^^world")).containsExactly("hello^^world");
        assertThat(MultiString.decode("^")).containsExactly("");
    }

    @Test
    void decodeCount() {
        assertThat(MultiString.decodeCount("")).isEqualTo(1);
        assertThat(MultiString.decodeCount("hello")).isEqualTo(1);
        assertThat(MultiString.decodeCount("hello|world")).isEqualTo(2);
        assertThat(MultiString.decodeCount("hello|world|")).isEqualTo(3);
        assertThat(MultiString.decodeCount("hello^|world")).isEqualTo(1);
        assertThat(MultiString.decodeCount("hello^^world")).isEqualTo(1);
        assertThat(MultiString.decodeCount("hello^^^^world")).isEqualTo(1);
    }

    @Test
    void encode() {
        assertThat(MultiString.encode(ImmutableList.of("hello", "world"))).isEqualTo("hello|world");
        assertThat(MultiString.encode(ImmutableList.of("hello", "world", ""))).isEqualTo("hello|world|");
        assertThat(MultiString.encode(ImmutableList.of("hello|world"))).isEqualTo("hello^|world");
        assertThat(MultiString.encode(ImmutableList.of("hello^world"))).isEqualTo("hello^^world");
        assertThat(MultiString.encode(ImmutableList.of("hello^^world"))).isEqualTo("hello^^^^world");
        assertThat(MultiString.encode(ImmutableList.of(""))).isEqualTo("");
        assertThatThrownBy(() -> MultiString.encode(ImmutableList.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
