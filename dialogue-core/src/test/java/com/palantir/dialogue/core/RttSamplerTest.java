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

import org.junit.jupiter.api.Test;

class RttSamplerTest {

    @Test
    void rtt_returns_the_min_of_the_last_5_measurements() {
        RttSampler.RttMeasurement rtt = new RttSampler.RttMeasurement();
        rtt.addMeasurement(3);
        assertThat(rtt.getRttNanos()).describedAs("%s", rtt).hasValue(3);
        rtt.addMeasurement(1);
        rtt.addMeasurement(2);
        assertThat(rtt.getRttNanos()).describedAs("%s", rtt).hasValue(1);

        rtt.addMeasurement(500);
        assertThat(rtt.getRttNanos()).describedAs("%s", rtt).hasValue(1);
        rtt.addMeasurement(500);
        rtt.addMeasurement(500);
        rtt.addMeasurement(500);
        assertThat(rtt.getRttNanos()).describedAs("%s", rtt).hasValue(2);
        rtt.addMeasurement(500);
        assertThat(rtt.getRttNanos()).describedAs("%s", rtt).hasValue(500);
    }
}
