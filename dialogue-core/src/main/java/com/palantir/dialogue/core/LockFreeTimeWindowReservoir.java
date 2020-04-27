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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Stores marks on a timeline of buckets, where marks are forgotten after the specified {@code memory}.
 * Reads and writes are lock-free and efficient.
 *
 * Replacement for {@link com.codahale.metrics.SlidingTimeWindowArrayReservoir}.
 */
@ThreadSafe
final class LockFreeTimeWindowReservoir {
    private final AtomicLongArray buckets;
    private final long bucketSizeNanos;
    private final Ticker clock;

    /** Cursor tells us which bucket to write marks into. Always less than buckets.length, always advances. */
    private final AtomicInteger cursor = new AtomicInteger(0);
    /** We maintain an extra count variable to serve fast reads - it should always equal the sum of the buckets. */
    private final AtomicLong count = new AtomicLong(0);
    /** Time in nanos when we need to move the cursor to the next bucket (and expire its contents). */
    private final AtomicLong nextRollover;

    LockFreeTimeWindowReservoir(Duration memory, int granularity, Ticker clock) {
        this.buckets = new AtomicLongArray(granularity);
        this.clock = clock;
        this.bucketSizeNanos = memory.toNanos() / granularity;
        this.nextRollover = new AtomicLong(clock.read() + bucketSizeNanos);
    }

    void mark() {
        maybeRoll();
        buckets.getAndIncrement(cursor.get());
        count.incrementAndGet();
    }

    long size() {
        maybeRoll();
        return count.get();
    }

    private void maybeRoll() {
        long scheduledRollover = nextRollover.get();
        long now = clock.read();
        if (now < scheduledRollover) {
            // it's not time yet, so no-op and we can keep writing to the bucket
            return;
        }

        long newScheduledRollover = rolloverTo(scheduledRollover);
        if (newScheduledRollover == -1 || newScheduledRollover > now) {
            // our work on this thread is done
            return;
        }

        // if this is the first mark in a long time, it's possible our cursor was very far behind, so we need to keep
        // rolling until the newScheduledRollover is actually in the future
        maybeRoll();
    }

    private long rolloverTo(long scheduledRollover) {
        int cursorRead = cursor.get();
        int newCursor = (cursorRead + 1) % buckets.length();

        // it doesn't matter if this CAS fails, it just means some other thread is doing the work and that's fine
        long newScheduledRollover = scheduledRollover + bucketSizeNanos;
        if (nextRollover.compareAndSet(scheduledRollover, newScheduledRollover)) {

            while (true) {
                long oldCount = count.get();
                long bucketContents = buckets.get(newCursor);

                // this CAS can fail if another mark() call incremented the count between the oldCount read and this
                // CAS, so we try again
                if (count.compareAndSet(oldCount, oldCount - bucketContents)) {
                    // doesn't matter if these fail, it means someone else has made the changes
                    buckets.compareAndSet(newCursor, bucketContents, 0);
                    cursor.compareAndSet(cursorRead, newCursor);
                    break;
                }
            }

            return newScheduledRollover;
        } else {
            return -1L;
        }
    }

    @Override
    public String toString() {
        return "SlidingTimeWindowReservoir{count="
                + count + ", cursor="
                + cursor + ", nextRollover="
                + nextRollover + ", bucketSizeNanos="
                + bucketSizeNanos + ", buckets="
                + buckets + '}';
    }
}
