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

package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServerTimingParserTest {

    @Test
    void testMissingValue() {
        assertThat(ServerTimingParser.getServerDurationNanos("missedCache", "missedCache"))
                .isEqualTo(ServerTimingParser.UNKNOWN);
    }

    @Test
    void testSingleMetricWithValue() {
        assertThat(ServerTimingParser.getServerDurationNanos("cpu;dur=2.4", "cpu"))
                .isEqualTo(2400000L);
    }

    @Test
    void testSingleMetricWithDescriptionAndValue() {
        assertThat(ServerTimingParser.getServerDurationNanos("cache;desc=\"Cache Read\";dur=23.2", "cache"))
                .isEqualTo(23200000L);
    }

    @Test
    void twoMetricsWithValue() {
        String headerValue = "db;dur=53, app;dur=47.2";
        assertThat(ServerTimingParser.getServerDurationNanos(headerValue, "db")).isEqualTo(53_000_000);
        assertThat(ServerTimingParser.getServerDurationNanos(headerValue, "app"))
                .isEqualTo(47_200_000);
    }
}
