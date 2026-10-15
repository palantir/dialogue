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
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Cause;
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Level;
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Result;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class ConcurrencyLimiterDecisionRecorderTest {

    @ParameterizedTest
    @CsvSource({
        "200, HTTP_2XX",
        "204, HTTP_2XX",
        "304, HTTP_3XX",
        "308, HTTP_3XX",
        "400, HTTP_4XX",
        "404, HTTP_4XX",
        "429, HTTP_429",
        "500, HTTP_500",
        "502, HTTP_5XX",
        "503, HTTP_503",
        "504, HTTP_5XX",
        "101, OTHER",
    })
    void classifiesResponses(int code, Decision_Cause expected) {
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(new TestResponse().code(code)))
                .isEqualTo(expected);
    }

    @Test
    void classifiesRetryOtherSeparatelyFromOtherRedirects() {
        TestResponse retryOther = new TestResponse().code(308).withHeader(HttpHeaders.LOCATION, "https://localhost");
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(retryOther)).isEqualTo(Decision_Cause.HTTP_308);
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
                    .isEqualTo(Decision_Cause.QOS_CUSTOM);
        }
    }

    @Test
    void classifiesFailures() {
        assertCause(new SocketTimeoutException("read timed out"), Decision_Cause.SOCKET_TIMEOUT);
        assertCause(new ConnectTimeoutException(), Decision_Cause.CONNECT_TIMEOUT);
        assertCause(new ConnectException("refused"), Decision_Cause.CONNECT_ERROR);
        assertCause(new NoRouteToHostException("no route"), Decision_Cause.CONNECT_ERROR);
        assertCause(new UnknownHostException("unknown"), Decision_Cause.UNKNOWN_HOST);
        assertCause(new SSLHandshakeException("handshake"), Decision_Cause.SSL);
        assertCause(new SocketException("Connection reset"), Decision_Cause.SOCKET_ERROR);
        assertCause(new IOException("other"), Decision_Cause.IO_ERROR);
        assertCause(new InterruptedIOException("aborted"), Decision_Cause.IO_ERROR);
        assertCause(new CancellationException(), Decision_Cause.CANCELLED);
        assertCause(new IllegalStateException(), Decision_Cause.OTHER);
    }

    @Test
    void classifiesWrappedFailuresByCause() {
        // Mirrors how the Apache client wraps connect timeouts in an IOException.
        assertCause(new IOException("wrapper", new ConnectTimeoutException()), Decision_Cause.CONNECT_TIMEOUT);
        assertCause(new IOException("wrapper", new SocketTimeoutException()), Decision_Cause.SOCKET_TIMEOUT);
        // Only IOExceptions affect the limiter, so wrapped IOExceptions in other throwables are not inspected.
        assertCause(new UncheckedIOException(new SocketTimeoutException()), Decision_Cause.OTHER);
    }

    @Test
    void boundsCauseDepth() {
        IOException deep = new IOException(
                "1", new IOException("2", new IOException("3", new SocketTimeoutException("too deep"))));
        assertCause(deep, Decision_Cause.IO_ERROR);
    }

    @Test
    void recordsDecisionMeters() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        ConcurrencyLimiterDecisionRecorder recorder =
                ConcurrencyLimiterDecisionRecorder.create(registry, "channel", Decision_Level.HOST);

        recorder.record(Decision_Result.DROPPED, Decision_Cause.SOCKET_TIMEOUT);
        recorder.record(Decision_Result.DROPPED, Decision_Cause.SOCKET_TIMEOUT);
        recorder.record(Decision_Result.SUCCESS, Decision_Cause.HTTP_2XX);

        assertThat(decisionMeter(registry, Decision_Level.HOST, Decision_Result.DROPPED, Decision_Cause.SOCKET_TIMEOUT)
                        .getCount())
                .isEqualTo(2);
        assertThat(decisionMeter(registry, Decision_Level.HOST, Decision_Result.SUCCESS, Decision_Cause.HTTP_2XX)
                        .getCount())
                .isEqualTo(1);
        assertThat(registry.getMetrics())
                .as("only observed combinations are registered")
                .hasSize(2);
    }

    static Meter decisionMeter(
            TaggedMetricRegistry registry, Decision_Level level, Decision_Result result, Decision_Cause cause) {
        return DialogueConcurrencylimiterMetrics.of(registry)
                .decision()
                .channelName("channel")
                .level(level)
                .result(result)
                .cause(cause)
                .build();
    }

    private static void assertCause(Throwable throwable, Decision_Cause expected) {
        assertThat(ConcurrencyLimiterDecisionRecorder.causeOf(throwable))
                .as("%s", throwable)
                .isEqualTo(expected);
    }

    /** Stands in for the Apache client's connect timeout, which dialogue-core cannot reference directly. */
    private static final class ConnectTimeoutException extends InterruptedIOException {
        ConnectTimeoutException() {
            super("connect timed out");
        }
    }
}
