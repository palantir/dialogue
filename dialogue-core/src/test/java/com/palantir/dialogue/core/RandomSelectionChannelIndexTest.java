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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.BitSet;
import org.junit.jupiter.api.Test;

final class RandomSelectionChannelIndexTest {

    @Test
    void testToIndex() {
        BitSet bitSet = new BitSet();
        assertThat(RandomSelectionChannel.toIndex(bitSet, 0)).isEqualTo(0);
        bitSet.set(0);
        assertThat(RandomSelectionChannel.toIndex(bitSet, 0)).isEqualTo(1);
    }
}
