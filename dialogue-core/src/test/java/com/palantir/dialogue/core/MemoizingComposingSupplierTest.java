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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;

public class MemoizingComposingSupplierTest {

    private AtomicInteger timesInvoked;
    private Function<Integer, Integer> function;
    private AtomicInteger value;
    private Supplier<Integer> supplier;

    @Before
    public void before() {
        timesInvoked = new AtomicInteger(0);
        function = in -> {
            timesInvoked.incrementAndGet();
            return in;
        };

        value = new AtomicInteger();
        supplier = new MemoizingComposingSupplier<>(value::get, function);
    }

    @Test
    public void testValueUnchanged() {
        value.set(0);
        assertThat(supplier.get()).isEqualTo(0);
        assertThat(supplier.get()).isEqualTo(0);
        assertThat(timesInvoked.get()).isEqualTo(1);
    }

    @Test
    public void testValueChanged() {
        value.set(0);
        assertThat(supplier.get()).isEqualTo(0);
        value.set(1);
        assertThat(supplier.get()).isEqualTo(1);

        assertThat(timesInvoked.get()).isEqualTo(2);
    }
}
