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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CoarseExponentialDecayTest {

    @Test
    void testDecay_toZero() {
        AtomicLong clock = new AtomicLong();
        CoarseExponentialDecay decay = new CoarseExponentialDecay(clock::get, Duration.ofNanos(10));
        assertThat(decay.get()).isZero();
        decay.increment();
        assertThat(decay.get()).isOne();
        clock.set(10);
        assertThat(decay.get()).isZero();
    }

    @Test
    void testDecay_byHalf() {
        AtomicLong clock = new AtomicLong();
        CoarseExponentialDecay decay = new CoarseExponentialDecay(clock::get, Duration.ofNanos(10));
        decay.increment();
        decay.increment();
        assertThat(decay.get()).isEqualTo(2);
        clock.set(10);
        assertThat(decay.get()).isOne();
        clock.set(20);
        assertThat(decay.get()).isZero();
    }

    @Test
    void testDecay_toZero_intervalsWithoutInteraction() {
        AtomicLong clock = new AtomicLong();
        CoarseExponentialDecay decay = new CoarseExponentialDecay(clock::get, Duration.ofNanos(10));
        decay.increment();
        decay.increment();
        assertThat(decay.get()).isEqualTo(2);
        clock.set(20);
        assertThat(decay.get()).isZero();
    }

    @Test
    void testDecay_intermediateDecay() {
        AtomicLong clock = new AtomicLong();
        CoarseExponentialDecay decay = new CoarseExponentialDecay(clock::get, Duration.ofNanos(10));
        for (int i = 0; i < 100; i++) {
            decay.increment();
        }
        assertThat(decay.get()).isEqualTo(100);
        clock.set(2);
        assertThat(decay.get())
                .as("Expected partial decay after a slice of the half life")
                .isLessThan(100)
                .isGreaterThan(50);
        clock.set(10);
        assertThat(decay.get()).isEqualTo(50);
    }
}
