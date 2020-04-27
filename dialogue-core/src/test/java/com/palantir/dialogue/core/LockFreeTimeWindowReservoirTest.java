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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LockFreeTimeWindowReservoirTest {

    Ticker clock = mock(Ticker.class);

    // hilariously poor granularity just for testing
    LockFreeTimeWindowReservoir reservoir = new LockFreeTimeWindowReservoir(Duration.ofSeconds(1), 5, clock);

    @Test
    void multiple_marks_into_one_bucket() {
        reservoir.mark();
        reservoir.mark();
        reservoir.mark();
        reservoir.mark();

        assertThat(reservoir.size()).isEqualTo(4);
    }

    @Test
    void multiple_marks_across_adjacent_buckets() {
        reservoir.mark();
        reservoir.mark();
        setTime(Duration.ofMillis(200));
        reservoir.mark();
        reservoir.mark();

        assertThat(reservoir.size()).isEqualTo(4);
        assertThat(reservoir)
                .hasToString("SlidingTimeWindowReservoir{count=4, cursor=1, nextRollover=400000000, "
                        + "bucketSizeNanos=200000000, buckets=[2, 2, 0, 0, 0]}");
    }

    @Test
    void multiple_marks_across_skipped_buckets() {
        reservoir.mark();
        reservoir.mark();
        setTime(Duration.ofMillis(600));
        reservoir.mark();
        reservoir.mark();
        reservoir.mark();
        reservoir.mark();
        reservoir.mark();
        reservoir.mark();

        assertThat(reservoir)
                .describedAs("This test demonstrates how we might have to call roll multiple times to skip past some "
                        + "buckets and catch up to where the cursor should *really* be")
                .hasToString("SlidingTimeWindowReservoir{count=8, cursor=3, nextRollover=800000000, "
                        + "bucketSizeNanos=200000000, buckets=[2, 0, 0, 6, 0]}");
    }

    @Test
    void mark_call_after_long_pause() {
        reservoir.mark();
        reservoir.mark();
        setTime(Duration.ofMillis(1500)); // longer than the entire memory of the reservoir
        reservoir.mark();

        assertThat(reservoir.toString()).contains("count=1").contains("buckets=[0, 0, 1, 0, 0]");
    }

    @Test
    void size_call_after_long_pause() {
        reservoir.mark();
        reservoir.mark();
        setTime(Duration.ofMillis(1500)); // longer than the entire memory of the reservoir

        assertThat(reservoir.size()).describedAs("Was %s", reservoir).isEqualTo(0);
    }

    private void setTime(Duration duration) {
        when(clock.read()).thenReturn(duration.toNanos());
    }
}
