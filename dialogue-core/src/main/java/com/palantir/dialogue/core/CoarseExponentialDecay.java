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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntBinaryOperator;
import java.util.function.LongSupplier;

/**
 * A Course exponential decay function which decays at fixed time intervals. This has a change of immediately
 * decaying new values such that some values are more impactful than others depending on when they are reported
 * in relation to the decay interval.
 */
final class CoarseExponentialDecay {

    @SuppressWarnings("UnnecessaryLambda") // Extract expensive allocations
    private static final IntBinaryOperator DECAY_FUNCTION = (value, decays) -> value >> decays;

    private final AtomicInteger value = new AtomicInteger();
    private final AtomicLong lastDecay = new AtomicLong();
    private final LongSupplier nanoClock;
    private final long decayIntervalNanoseconds;

    CoarseExponentialDecay(LongSupplier nanoClock, Duration halfLife) {
        this.nanoClock = nanoClock;
        this.decayIntervalNanoseconds = halfLife.toNanos();
        lastDecay.set(nanoClock.getAsLong());
    }

    void increment() {
        decayIfNecessary();
        value.incrementAndGet();
    }

    int get() {
        // Potential optimization if the value is zero
        decayIfNecessary();
        return value.get();
    }

    void decayIfNecessary() {
        // Invoke the clock syscall prior to taking a snapshot of lastDecay to minimize time between compare and
        // swap.
        long now = nanoClock.getAsLong();
        long lastDecaySnapshot = lastDecay.get();
        long nanosSinceLastDecay = now - lastDecaySnapshot;
        // Decay rate must catch up when no updates or reads have occurred over a period of time
        int decays = (int) (nanosSinceLastDecay / decayIntervalNanoseconds);
        // nanoTime values wrap, it's important that values are used in comparison
        if (decays > 0
                && lastDecay.compareAndSet(lastDecaySnapshot, lastDecaySnapshot + decays * decayIntervalNanoseconds)) {
            value.accumulateAndGet(decays, DECAY_FUNCTION);
        }
    }

    @Override
    public String toString() {
        return "CoarseExponentialDecay{value=" + value + ", decayIntervalNanoseconds=" + decayIntervalNanoseconds + '}';
    }
}
