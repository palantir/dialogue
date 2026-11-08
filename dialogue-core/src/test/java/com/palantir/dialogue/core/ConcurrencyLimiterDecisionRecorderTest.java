/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Meter;
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Result;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import org.junit.jupiter.api.Test;

final class ConcurrencyLimiterDecisionRecorderTest {

    @Test
    void recordsDecisionMeters() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        ConcurrencyLimiterDecisionRecorder recorder = ConcurrencyLimiterDecisionRecorder.create(registry, "channel", 3);

        recorder.record(Decision_Result.DROPPED, "SocketTimeoutException");
        recorder.record(Decision_Result.DROPPED, "SocketTimeoutException");
        recorder.record(Decision_Result.SUCCESS, "http-2xx");

        assertThat(decisionMeter(registry, "3", Decision_Result.DROPPED, "SocketTimeoutException")
                        .getCount())
                .isEqualTo(2);
        assertThat(decisionMeter(registry, "3", Decision_Result.SUCCESS, "http-2xx")
                        .getCount())
                .isEqualTo(1);
        assertThat(registry.getMetrics())
                .as("only observed combinations are registered")
                .hasSize(2);
    }

    static Meter decisionMeter(TaggedMetricRegistry registry, String hostIndex, Decision_Result result, String cause) {
        return DialogueConcurrencylimiterMetrics.of(registry)
                .decision()
                .channelName("channel")
                .hostIndex(hostIndex)
                .result(result)
                .cause(cause)
                .build();
    }
}
