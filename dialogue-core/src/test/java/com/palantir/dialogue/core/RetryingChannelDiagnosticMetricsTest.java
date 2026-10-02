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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.DialogueRetries;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RetriesExhaustedException;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.DialogueClientMetrics.RequestRetryDiagnosticRequests_Result;
import com.palantir.dialogue.core.DialogueClientMetrics.RequestRetryDiagnosticRetries_Result;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.Test;

final class RetryingChannelDiagnosticMetricsTest {
    private static final String CHANNEL_NAME = "diagnostic-channel";
    private static final Request REQUEST = Request.builder().build();

    private final EndpointChannel delegate = mock(EndpointChannel.class);
    private final TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
    private final DeterministicScheduler scheduler = new DeterministicScheduler();

    @Test
    void records_all_retries_after_marker_only_when_logical_request_completes() {
        SettableFuture<Response> finalAttempt = SettableFuture.create();
        when(delegate.execute(REQUEST))
                .thenReturn(Futures.immediateFuture(exhaustedResponse(503)))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(503)))
                .thenReturn(Futures.immediateFailedFuture(new IOException("retryable failure")))
                .thenReturn(finalAttempt);
        EndpointChannel retryer = retryer(3, Duration.ofSeconds(1));

        ListenableFuture<Response> result = retryer.execute(REQUEST);
        scheduler.tick(7, TimeUnit.SECONDS);

        verify(delegate, times(4)).execute(REQUEST);
        assertThat(result).isNotDone();
        assertDiagnosticMetrics(0, 0, 0, 0);

        TestResponse success = new TestResponse().code(204);
        finalAttempt.set(success);

        assertThat(result).succeedsWithin(Duration.ZERO).isSameAs(success);
        assertDiagnosticMetrics(3, 1, 0, 0);
        assertThat(DialogueRetries.isRetriesExhausted(success)).isFalse();

        // A new logical call does not inherit the preceding call's diagnostic state.
        assertThat(retryer.execute(REQUEST)).succeedsWithin(Duration.ZERO).isSameAs(success);
        assertDiagnosticMetrics(3, 1, 0, 0);
    }

    @Test
    void records_non_retryable_response_as_failure_without_propagating_historical_marker() {
        TestResponse terminalResponse = new TestResponse().code(400);
        when(delegate.execute(REQUEST))
                .thenReturn(Futures.immediateFuture(exhaustedResponse(503)))
                .thenReturn(Futures.immediateFuture(terminalResponse));

        ListenableFuture<Response> result = retryer(2, Duration.ZERO).execute(REQUEST);

        assertThat(result).succeedsWithin(Duration.ZERO).isSameAs(terminalResponse);
        verify(delegate, times(2)).execute(REQUEST);
        assertDiagnosticMetrics(0, 0, 1, 1);
        assertThat(DialogueRetries.isRetriesExhausted(terminalResponse)).isFalse();
    }

    @Test
    void records_terminal_throwable_once_despite_enclosing_retry_callbacks() {
        IOException failure = new IOException("terminal failure");
        when(delegate.execute(REQUEST))
                .thenReturn(Futures.immediateFuture(exhaustedResponse(503)))
                .thenReturn(Futures.immediateFailedFuture(failure));

        ListenableFuture<Response> result = retryer(1, Duration.ZERO).execute(REQUEST);

        assertThat(result)
                .failsWithin(Duration.ZERO)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(RetriesExhaustedException.class)
                .havingCause()
                .isSameAs(failure);
        verify(delegate, times(2)).execute(REQUEST);
        assertDiagnosticMetrics(0, 0, 1, 1);
    }

    @Test
    void retries_without_incoming_marker_do_not_record_diagnostics_even_when_exhausted() {
        TestResponse terminalResponse = new TestResponse().code(503);
        when(delegate.execute(REQUEST))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(503)))
                .thenReturn(Futures.immediateFuture(terminalResponse));

        ListenableFuture<Response> result = retryer(1, Duration.ZERO).execute(REQUEST);

        assertThat(result).succeedsWithin(Duration.ZERO).isSameAs(terminalResponse);
        verify(delegate, times(2)).execute(REQUEST);
        assertThat(DialogueRetries.isRetriesExhausted(terminalResponse)).isTrue();
        assertDiagnosticMetrics(0, 0, 0, 0);
    }

    @Test
    void marker_without_subsequent_retry_does_not_record_diagnostics() {
        TestResponse terminalResponse = exhaustedResponse(400);
        when(delegate.execute(REQUEST)).thenReturn(Futures.immediateFuture(terminalResponse));

        ListenableFuture<Response> result = retryer(1, Duration.ZERO).execute(REQUEST);

        assertThat(result).succeedsWithin(Duration.ZERO).isSameAs(terminalResponse);
        verify(delegate).execute(REQUEST);
        assertDiagnosticMetrics(0, 0, 0, 0);
    }

    @Test
    void cancellation_of_in_flight_retry_records_failure() {
        SettableFuture<Response> retryAttempt = SettableFuture.create();
        when(delegate.execute(REQUEST))
                .thenReturn(Futures.immediateFuture(exhaustedResponse(503)))
                .thenReturn(retryAttempt);

        ListenableFuture<Response> result = retryer(1, Duration.ZERO).execute(REQUEST);

        verify(delegate, times(2)).execute(REQUEST);
        assertDiagnosticMetrics(0, 0, 0, 0);
        assertThat(result.cancel(true)).isTrue();
        assertThat(retryAttempt).isCancelled();
        assertDiagnosticMetrics(0, 0, 1, 1);
    }

    @Test
    void cancellation_before_first_retry_dispatch_counts_scheduled_retry() {
        when(delegate.execute(REQUEST)).thenReturn(Futures.immediateFuture(exhaustedResponse(503)));

        ListenableFuture<Response> result = retryer(1, Duration.ofSeconds(1)).execute(REQUEST);

        assertThat(result).isNotDone();
        assertDiagnosticMetrics(0, 0, 0, 0);
        assertThat(DialogueClientMetrics.of(registry)
                        .requestRetry()
                        .channelName(CHANNEL_NAME)
                        .reason("qosResponse")
                        .build()
                        .getCount())
                .isEqualTo(1);
        assertThat(result.cancel(true)).isTrue();
        assertDiagnosticMetrics(0, 0, 1, 1);

        scheduler.tick(10, TimeUnit.SECONDS);

        verify(delegate).execute(REQUEST);
        assertDiagnosticMetrics(0, 0, 1, 1);
    }

    @Test
    void cancellation_during_later_backoff_counts_all_scheduled_retries() {
        when(delegate.execute(REQUEST))
                .thenReturn(Futures.immediateFuture(exhaustedResponse(503)))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(503)));

        ListenableFuture<Response> result = retryer(2, Duration.ofSeconds(1)).execute(REQUEST);
        scheduler.tick(1, TimeUnit.SECONDS);

        verify(delegate, times(2)).execute(REQUEST);
        assertThat(result).isNotDone();
        assertDiagnosticMetrics(0, 0, 0, 0);
        assertThat(DialogueClientMetrics.of(registry)
                        .requestRetry()
                        .channelName(CHANNEL_NAME)
                        .reason("qosResponse")
                        .build()
                        .getCount())
                .isEqualTo(2);
        assertThat(result.cancel(true)).isTrue();
        assertDiagnosticMetrics(0, 0, 2, 1);

        scheduler.tick(10, TimeUnit.SECONDS);

        verify(delegate, times(2)).execute(REQUEST);
        assertDiagnosticMetrics(0, 0, 2, 1);
    }

    private EndpointChannel retryer(int maxRetries, Duration backoffSlotSize) {
        return new RetryingChannel(
                delegate,
                TestEndpoint.GET,
                CHANNEL_NAME,
                registry,
                maxRetries,
                backoffSlotSize,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED,
                scheduler,
                () -> 1.0);
    }

    private static TestResponse exhaustedResponse(int statusCode) {
        TestResponse response = new TestResponse().code(statusCode);
        DialogueRetries.setRetriesExhausted(response);
        return response;
    }

    private void assertDiagnosticMetrics(
            long successRetries, long successRequests, long failureRetries, long failureRequests) {
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        assertThat(metrics.requestRetryDiagnosticRetries()
                        .channelName(CHANNEL_NAME)
                        .result(RequestRetryDiagnosticRetries_Result.SUCCESS)
                        .build()
                        .getCount())
                .as("successful diagnostic retries")
                .isEqualTo(successRetries);
        assertThat(metrics.requestRetryDiagnosticRequests()
                        .channelName(CHANNEL_NAME)
                        .result(RequestRetryDiagnosticRequests_Result.SUCCESS)
                        .build()
                        .getCount())
                .as("successful diagnostic requests")
                .isEqualTo(successRequests);
        assertThat(metrics.requestRetryDiagnosticRetries()
                        .channelName(CHANNEL_NAME)
                        .result(RequestRetryDiagnosticRetries_Result.FAILURE)
                        .build()
                        .getCount())
                .as("failed diagnostic retries")
                .isEqualTo(failureRetries);
        assertThat(metrics.requestRetryDiagnosticRequests()
                        .channelName(CHANNEL_NAME)
                        .result(RequestRetryDiagnosticRequests_Result.FAILURE)
                        .build()
                        .getCount())
                .as("failed diagnostic requests")
                .isEqualTo(failureRequests);
    }
}
