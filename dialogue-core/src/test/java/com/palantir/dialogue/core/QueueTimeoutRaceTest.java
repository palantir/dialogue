/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.QueuedChannel.QueuedChannelInstrumentation;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for queue timeout race conditions.
 *
 * <p>The "timeout while queued" tests use a real {@link ScheduledExecutorService} with a short timeout
 * and {@link Thread#sleep} to let the scheduled task fire. The "timeout after dispatch" tests simulate
 * the race by calling {@link SettableFuture#setException} directly on the caller's future — this is
 * exactly what the scheduled task does, so it exercises the same code path without needing precise
 * thread interleaving.
 *
 * <p>The harness uses a {@link ControllableDelegate} that can be toggled between accepting and
 * rejecting requests, giving us precise control over when requests are queued vs dispatched.
 */
class QueueTimeoutRaceTest {

    private static final Endpoint ENDPOINT = TestEndpoint.POST;
    private static final Request REQUEST = Request.builder().build();
    private static final Duration QUEUE_TIMEOUT = Duration.ofMillis(100);

    /**
     * A {@link LimitedChannel} that can be toggled between accepting and rejecting.
     * When accepting, it returns a {@link SettableFuture} that the test controls.
     *
     * <p>Respects {@link LimitEnforcement}: when limits are bypassed ({@code DANGEROUS_BYPASS_LIMITS}),
     * the delegate always accepts — matching real {@link ConcurrencyLimitedChannel} behavior.
     * This is important because {@link QueuedChannel} uses bypass when {@code inFlight == 0}
     * and treats a rejection under bypass as a fatal error.
     */
    static final class ControllableDelegate implements LimitedChannel {
        private volatile boolean accepting;
        private final Queue<SettableFuture<Response>> dispatched = new ConcurrentLinkedQueue<>();

        void setAccepting(boolean value) {
            this.accepting = value;
        }

        SettableFuture<Response> lastDispatched() {
            return dispatched.poll();
        }

        @Override
        @NullMarked
        public Optional<ListenableFuture<Response>> maybeExecute(
                Endpoint _endpoint, Request _request, LimitEnforcement limitEnforcement) {
            if (!accepting && limitEnforcement.enforceLimits()) {
                return Optional.empty();
            }
            SettableFuture<Response> future = SettableFuture.create();
            dispatched.add(future);
            return Optional.of(future);
        }
    }

    private ControllableDelegate delegate;
    private QueuedChannel queuedChannel;
    private ScheduledExecutorService scheduler;
    private static final String TIMEOUT_SCHEDULER_NAME = "test-scheduler";

    @BeforeEach
    @SuppressWarnings("deprecation")
    void beforeEach() {
        delegate = new ControllableDelegate();
        scheduler = DialogueExecutors.newSharedSingleThreadScheduler(MetricRegistries.instrument(
                SharedTaggedMetricRegistries.getSingleton(),
                new ThreadFactoryBuilder()
                        .setNameFormat(TIMEOUT_SCHEDULER_NAME + "-%d")
                        .setDaemon(true)
                        .build(),
                TIMEOUT_SCHEDULER_NAME));

        DialogueClientMetrics metrics = DialogueClientMetrics.of(new DefaultTaggedMetricRegistry());
        QueuedChannelInstrumentation instrumentation = new QueuedChannelInstrumentation() {
            @Override
            @NullMarked
            public Counter requestsQueued() {
                return metrics.requestsQueued("race-test");
            }

            @Override
            @NullMarked
            public Timer requestQueuedTime() {
                return metrics.requestQueuedTime("race-test");
            }
        };

        queuedChannel = new QueuedChannel(
                delegate, "race-test", "channel", instrumentation, 100, QUEUE_TIMEOUT.toNanos(), scheduler);
    }

    @AfterEach
    void afterEach() {
        scheduler.shutdownNow();
    }

    /**
     * Sends one request that the delegate accepts (via DANGEROUS_BYPASS_LIMITS),
     * putting QueuedChannel into inFlight > 0 state. Subsequent requests that the
     * delegate rejects will be queued instead of hitting the bypass failure path.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private void saturateWithOneRequest() {
        delegate.setAccepting(true);
        queuedChannel.execute(ENDPOINT, REQUEST);
        delegate.setAccepting(false);
    }

    @Nested
    @SuppressWarnings("FutureReturnValueIgnored")
    class TimeoutWhileQueued {

        @Test
        void caller_future_is_failed_with_timeout() throws Exception {
            saturateWithOneRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(ENDPOINT, REQUEST);
            assertThat(callerFuture).isNotDone();
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(1);

            // Wait for the scheduled timeout task to fire
            Thread.sleep(QUEUE_TIMEOUT.toMillis() + 50);

            assertThat(callerFuture).isDone();
            assertThat(callerFuture)
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .withMessageContaining("queue timeout");
        }

        @Test
        void deque_entry_is_cleaned_up_on_next_drain() throws Exception {
            saturateWithOneRequest();

            queuedChannel.execute(ENDPOINT, REQUEST);
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(1);

            // Wait for timeout to fire
            Thread.sleep(QUEUE_TIMEOUT.toMillis() + 50);

            // Entry is still in the main deque (timeout only called setException).
            // Trigger a drain by making the delegate accept and calling schedule.
            delegate.setAccepting(true);
            queuedChannel.schedule();

            // scheduleNextTask sees isDone() → true, cleans up
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(0);
        }
    }

    @Nested
    class TimeoutAfterDispatchWirePending {

        @Test
        @SuppressWarnings("FutureReturnValueIgnored") // intentionally skip saturating request's wire future
        void caller_gets_timeout_and_wire_response_is_closed() {
            saturateWithOneRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(ENDPOINT, REQUEST);
            assertThat(callerFuture).isNotDone();
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(1);

            // Dispatch the queued request before the timeout fires
            delegate.setAccepting(true);
            queuedChannel.schedule();
            // Skip the saturating request's future
            delegate.lastDispatched();
            SettableFuture<Response> wireFuture = delegate.lastDispatched();
            assertThat(wireFuture).as("Request should have been dispatched").isNotNull();

            // Simulate timeout firing AFTER dispatch but BEFORE wire response.
            // This is exactly what the scheduled task does: call setException on the
            // caller's SettableFuture. ForwardAndSchedule links wireFuture → callerFuture.
            ((SettableFuture<Response>) callerFuture)
                    .setException(new SafeRuntimeException("Request queued for longer than queue timeout"));

            // Sanity-check that the above `setException` all ran
            assertThat(callerFuture).isDone();

            // Wire response arrives AFTER the caller already got the timeout.
            // ForwardAndSchedule.onSuccess calls response.set(result) → false → close()
            TestResponse wireResponse = new TestResponse().code(200);
            wireFuture.set(wireResponse);
            assertThat(wireResponse.isClosed())
                    .as("Wire response must be closed since caller future was already completed")
                    .isTrue();
        }
    }

    @Nested
    class TimeoutAfterDispatchAndWireCompletion {

        @Test
        @SuppressWarnings("FutureReturnValueIgnored") // intentionally skip saturating request's wire future
        void timeout_is_noop_caller_has_response() throws ExecutionException, InterruptedException {
            saturateWithOneRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(ENDPOINT, REQUEST);

            // Dispatch
            delegate.setAccepting(true);
            queuedChannel.schedule();
            delegate.lastDispatched();
            SettableFuture<Response> wireFuture = delegate.lastDispatched();

            // Wire completes successfully BEFORE timeout
            TestResponse wireResponse = new TestResponse().code(200);
            wireFuture.set(wireResponse);
            assertThat(callerFuture).isDone();
            assertThat(callerFuture.get().code()).isEqualTo(200);

            // Timeout fires — setException returns false, no-op
            boolean didTimeout = ((SettableFuture<Response>) callerFuture)
                    .setException(new SafeRuntimeException("Request queued for longer than queue timeout"));
            assertThat(didTimeout)
                    .as("setException should return false — future already completed with the wire response")
                    .isFalse();

            // Caller still has the original 200 response, not the timeout exception
            assertThat(callerFuture.get().code()).isEqualTo(200);
            assertThat(wireResponse.isClosed())
                    .as("Response should NOT be closed — caller owns it")
                    .isFalse();
        }
    }

    @Nested
    class TimeoutDuringRequeue {

        @Test
        void re_queued_entry_is_cleaned_up_after_timeout() throws Exception {
            saturateWithOneRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(ENDPOINT, REQUEST);
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(1);

            // schedule() runs: polls entry, tries delegate → rejects, offerFirst back
            queuedChannel.schedule();
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(1);
            assertThat(callerFuture).isNotDone();

            // Wait for the scheduled timeout to fire
            Thread.sleep(QUEUE_TIMEOUT.toMillis() + 50);

            assertThat(callerFuture).isDone();
            assertThat(callerFuture)
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .withMessageContaining("queue timeout");

            // Next drain sees isDone → true, cleans up
            delegate.setAccepting(true);
            queuedChannel.schedule();
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(0);
        }
    }

    @Nested
    class CallerCancellation {

        @Test
        void cancel_while_queued_prevents_dispatch() {
            saturateWithOneRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(ENDPOINT, REQUEST);
            assertThat(callerFuture).isNotDone();
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(1);

            // Caller cancels before timeout fires
            callerFuture.cancel(true);
            assertThat(callerFuture).isCancelled();

            // Drain — scheduleNextTask sees isDone() → true, cleans up without dispatching
            delegate.setAccepting(true);
            queuedChannel.schedule();
            assertThat(queuedChannel.getQueueSizeForTesting()).isEqualTo(0);
        }

        @Test
        @SuppressWarnings("FutureReturnValueIgnored") // intentionally skip saturating request's wire future
        void cancel_after_dispatch_propagates_to_wire_future() {
            saturateWithOneRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(ENDPOINT, REQUEST);

            // Dispatch
            delegate.setAccepting(true);
            queuedChannel.schedule();
            delegate.lastDispatched(); // skip saturating
            SettableFuture<Response> wireFuture = delegate.lastDispatched();
            assertThat(wireFuture).isNotNull();

            // Caller cancels after dispatch, wire still pending.
            // QueuedChannel's listener propagates cancellation to the wire future.
            callerFuture.cancel(true);
            assertThat(callerFuture).isCancelled();
            assertThat(wireFuture).isCancelled();
        }

        @Test
        @SuppressWarnings("FutureReturnValueIgnored")
        void timeout_after_cancel_is_noop() throws Exception {
            saturateWithOneRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(ENDPOINT, REQUEST);
            assertThat(callerFuture).isNotDone();

            // Caller cancels
            callerFuture.cancel(true);
            assertThat(callerFuture).isCancelled();

            // Timeout fires — setException returns false because future is already cancelled
            boolean didTimeout = ((SettableFuture<Response>) callerFuture)
                    .setException(new SafeRuntimeException("Request queued for longer than queue timeout"));
            assertThat(didTimeout)
                    .as("setException should return false — future was already cancelled")
                    .isFalse();
        }
    }
}
