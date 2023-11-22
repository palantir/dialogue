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

import com.google.common.util.concurrent.AtomicDouble;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongSupplier;

/**
 * A Course exponential decay function which decays at fixed time intervals. This has a change of immediately
 * decaying new values such that some values are more impactful than others depending on when they are reported
 * in relation to the decay interval.
 * Unlike implementations in common metrics libraries, this is optimized for reads rather than writes.
 */
final class CoarseExponentialDecayReservoir {

    // DECAY_FACTOR ^ DECAYS_PER_HALF_LIFE == .5
    // Several decays occur per half life to produce smoother traffic curves.
    private static final int DECAYS_PER_HALF_LIFE = 10;
    private static final double DECAY_FACTOR = Math.pow(.5D, 1D / DECAYS_PER_HALF_LIFE);

    @SuppressWarnings("UnnecessaryLambda") // no allocations
    private static final DoubleBinaryOperator DECAY = (value, decacyFactor) -> value * decacyFactor;

    private final AtomicDouble value = new AtomicDouble();
    /** System precise clock time (nanoseconds) of the last decay. */
    private final AtomicLong lastDecay = new AtomicLong();

    private final LongSupplier nanoClock;
    private final long decayIntervalNanoseconds;

    CoarseExponentialDecayReservoir(LongSupplier nanoClock, Duration halfLife) {
        this.nanoClock = nanoClock;
        this.decayIntervalNanoseconds = halfLife.toNanos() / DECAYS_PER_HALF_LIFE;
        lastDecay.set(nanoClock.getAsLong());
    }

    void update(double updates) {
        decayIfNecessary();
        value.addAndGet(updates);
    }

    double get() {
        // Potential optimization if the value is zero
        decayIfNecessary();
        return value.get();
    }

    private void decayIfNecessary() {
        // Invoke the clock syscall prior to taking a snapshot of lastDecay to minimize time between compare and
        // swap.
        long now = nanoClock.getAsLong();
        long lastDecaySnapshot = lastDecay.get();
        long nanosSinceLastDecay = now - lastDecaySnapshot;
        // Decay rate must catch up when no updates or reads have occurred over a period of time
        int decays = (int) (nanosSinceLastDecay / decayIntervalNanoseconds);
        // nanoTime values wrap, it's important that values are used in comparison
        if (decays > 0
                // On CAS failure another thread has successfully executed the decay function.
                // It's possible the current thread may read or update the value before a decay has
                // completed, but ultimately it makes little difference due to smaller segmented decays.
                && lastDecay.compareAndSet(lastDecaySnapshot, lastDecaySnapshot + decays * decayIntervalNanoseconds)) {
            value.accumulateAndGet(Math.pow(DECAY_FACTOR, decays), DECAY);
        }
    }

    @Override
    public String toString() {
        return "CoarseExponentialDecay{value=" + value + ", decayIntervalNanoseconds=" + decayIntervalNanoseconds + '}';
    }
}
