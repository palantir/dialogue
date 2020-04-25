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

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Stores marks on a timeline of buckets, where marks are forgotten after the specified {@code memory}.
 * Reads and writes are lock-free and efficient.
 *
 * Replacement for {@link com.codahale.metrics.SlidingTimeWindowArrayReservoir}.
 */
@ThreadSafe
final class SlidingTimeWindowReservoir {
    private final long[] buckets;
    private final long bucketSizeNanos;
    private final Ticker clock;

    private volatile int cursor;
    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong nextRollover;

    SlidingTimeWindowReservoir(Duration memory, int granularity, Ticker clock) {
        this.buckets = new long[granularity];
        this.clock = clock;
        this.bucketSizeNanos = memory.toNanos() / granularity;
        this.nextRollover = new AtomicLong(clock.read() + memory.toNanos());
    }

    void mark() {
        maybeRoll();
        count.incrementAndGet();
        buckets[cursor] += 1;
    }

    long size() {
        return count.get();
    }

    private void maybeRoll() {
        long next = nextRollover.get();
        if (clock.read() < next) {
            return;
        }

        // only one thread does the rolling, because it requires changing a few variables in lock-step
        if (nextRollover.compareAndSet(next, next + bucketSizeNanos)) {

            int newCursor = (cursor + 1) % buckets.length;

            // before we can start writing into the next window, we must ensure it's no longer represented in the count
            while (true) {
                long oldCount = count.get();
                long bucketContents = buckets[newCursor];

                // this CAS can fail if someone else increments the count between our read and the decrement, so we just
                // try again.
                if (count.compareAndSet(oldCount, oldCount - bucketContents)) {
                    buckets[newCursor] = 0;
                    cursor = newCursor;
                    return;
                }
            }
        }
    }
}
