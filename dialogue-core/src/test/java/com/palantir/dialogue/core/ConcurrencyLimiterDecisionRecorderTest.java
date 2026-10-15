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
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.QosReason.DueTo;
import com.palantir.conjure.java.api.errors.QosReasons;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TestResponseQosEncoder;
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Result;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class ConcurrencyLimiterDecisionRecorderTest {

    @ParameterizedTest
    @CsvSource({
        "200, http-2xx",
        "204, http-2xx",
        "304, http-3xx",
        "308, http-3xx",
        "400, http-4xx",
        "404, http-4xx",
        "429, http-429",
        "500, http-500",
        "502, http-5xx",
        "503, http-503",
        "504, http-5xx",
        "101, other",
    })
    void classifiesResponses(int code, String expected) {
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(new TestResponse().code(code)))
                .isEqualTo(expected);
    }

    @Test
    void classifiesRetryOtherSeparatelyFromOtherRedirects() {
        TestResponse retryOther = new TestResponse().code(308).withHeader(HttpHeaders.LOCATION, "https://localhost");
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(retryOther)).isEqualTo("http-308");
    }

    @Test
    void classifiesCustomQos() {
        for (int code : new int[] {429, 503}) {
            TestResponse response = new TestResponse().code(code);
            QosReasons.encodeToResponse(
                    QosReason.builder().reason("reason").dueTo(DueTo.CUSTOM).build(),
                    response,
                    TestResponseQosEncoder.INSTANCE);
            assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(response))
                    .as("status %d", code)
                    .isEqualTo("qos-custom");
        }
    }

    @Test
    void usesExceptionSimpleName() {
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(new SocketTimeoutException("read timed out")))
                .isEqualTo("SocketTimeoutException");
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(new UnknownHostException("unknown")))
                .isEqualTo("UnknownHostException");
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(new CancellationException()))
                .isEqualTo("CancellationException");
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(new IOException("wrapper", new SocketTimeoutException())))
                .as("only the outermost exception is used, as in dialogue.client.request.retry")
                .isEqualTo("IOException");
    }

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
