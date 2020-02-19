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

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SafeThreadLocalRandomTest {

    @Test
    void test_nextInt() {
        testCreatesDistinctValues(SafeThreadLocalRandom.get()::nextInt);
    }

    @Test
    void test_nextBoolean() {
        testCreatesDistinctValues(SafeThreadLocalRandom.get()::nextBoolean);
    }

    @Test
    void test_nextLong() {
        testCreatesDistinctValues(SafeThreadLocalRandom.get()::nextLong);
    }

    @Test
    void test_nextFloat() {
        testCreatesDistinctValues(SafeThreadLocalRandom.get()::nextFloat);
    }

    @Test
    void test_nextDouble() {
        testCreatesDistinctValues(SafeThreadLocalRandom.get()::nextDouble);
    }

    @Test
    void test_nextGaussian() {
        testCreatesDistinctValues(SafeThreadLocalRandom.get()::nextGaussian);
    }

    private <T> void testCreatesDistinctValues(Supplier<T> supplier) {
        Set<T> values = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            values.add(supplier.get());
        }
        assertThat(values).hasSizeGreaterThan(1);
    }
}
