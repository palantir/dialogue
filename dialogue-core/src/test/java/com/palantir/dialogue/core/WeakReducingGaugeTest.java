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
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class WeakReducingGaugeTest {
    @Test
    public void empty_initially() {
        WeakReducingGauge<String> gauge = new WeakReducingGauge<>(String::length, Integer::sum);
        assertThat(gauge.getValue()).isEqualTo(0);
    }

    @Test
    public void sums_correctly() {
        WeakReducingGauge<String> gauge = new WeakReducingGauge<>(String::length, Integer::sum);
        gauge.add("Hello");
        gauge.add("World");
        assertThat(gauge.getValue()).isEqualTo(10);
    }

    @Test
    public void source_items_deleted_when_no_remaining_references_and_gc() {
        class SomeObject {}

        WeakReducingGauge<SomeObject> gauge = new WeakReducingGauge<>(item -> 1, Integer::sum);
        gauge.add(new SomeObject());
        gauge.add(new SomeObject());
        SomeObject preserve = new SomeObject();
        gauge.add(preserve);
        assertThat(gauge.getValue()).isEqualTo(3);

        System.gc();

        assertThat(gauge.getValue()).isEqualTo(1);
    }
}

