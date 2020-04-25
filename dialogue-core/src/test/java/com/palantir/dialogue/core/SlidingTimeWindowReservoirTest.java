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
class SlidingTimeWindowReservoirTest {

    Ticker clock = mock(Ticker.class);

    // hilariously poor granularity just for testing
    SlidingTimeWindowReservoir reservoir = new SlidingTimeWindowReservoir(Duration.ofSeconds(1), 5, clock);

    @Test
    void multiple_marks_into_one_bucket() {
        reservoir.mark();
        reservoir.mark();
        reservoir.mark();
        reservoir.mark();

        assertThat(reservoir.size()).isEqualTo(4);
    }

    @Test
    void multiple_marks_across_two_buckets() {
        reservoir.mark();
        reservoir.mark();
        setTime(Duration.ofMillis(200));
        reservoir.mark();
        reservoir.mark();

        assertThat(reservoir.size()).isEqualTo(4);
    }

    private void setTime(Duration duration) {
        when(clock.read()).thenReturn(duration.toNanos());
    }
}
