/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.QueuedChannel.QueuedChannelInstrumentation;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.TestTracing;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("FutureReturnValueIgnored")
public class QueuedChannelTest {

    private static QueuedChannel createQueue(LimitedChannel delegate) {
        return createQueue(delegate, 100_000);
    }

    private static QueuedChannel createQueue(LimitedChannel delegate, int maxQueueSize) {
        String channelName = "my-channel";
        return createQueue(
                delegate,
                channelName,
                "queue-type",
                QueuedChannel.channelInstrumentation(
                        DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), channelName),
                maxQueueSize);
    }

    // Builds a QueuedChannel with no queue timeout configured: empty budget, system ticker, and no scheduler.
    private static QueuedChannel createQueue(
            LimitedChannel delegate,
            String channelName,
            String queueType,
            QueuedChannelInstrumentation instrumentation,
            int maxQueueSize) {
        return new QueuedChannel(
                delegate,
                channelName,
                queueType,
                instrumentation,
                maxQueueSize,
                OptionalLong.empty(),
                Ticker.systemTicker(),
                null);
    }

    @Test
    public void testReceivesSuccessfulResponse() throws ExecutionException, InterruptedException {
        SettableFuture<Response> result = SettableFuture.create();
        LimitedChannel delegateChannel = (_endpoint, _request, _limitEnforcement) -> Optional.of(result);
        QueuedChannel queued = createQueue(delegateChannel);
        ListenableFuture<Response> response =
                queued.maybeExecute(TestEndpoint.GET, Request.builder().build()).get();
        assertThat(response.isDone()).isFalse();

        Response expectedResponse = new TestResponse().code(200);
        result.set(expectedResponse);

        assertThat(response.isDone()).isTrue();
        assertThat(response.get()).isEqualTo(expectedResponse);
    }

    @Test
    public void testReceivesExceptionalResponse() {
        SettableFuture<Response> result = SettableFuture.create();
        LimitedChannel delegateChannel = (_endpoint, _request, _limitEnforcement) -> Optional.of(result);
        QueuedChannel queued = createQueue(delegateChannel);
        ListenableFuture<Response> response =
                queued.maybeExecute(TestEndpoint.GET, Request.builder().build()).get();
        assertThat(response.isDone()).isFalse();

        result.setException(new IllegalArgumentException());

        assertThat(response.isDone()).isTrue();
        assertThatThrownBy(response::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testQueuedRequestExecutedOnNextSubmission() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        AtomicBoolean hasCapacity = new AtomicBoolean(false);
        LimitedChannel delegateChannel = (_endpoint, _request, limitEnforcement) -> {
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (hasCapacity.get() || !limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannel queued = createQueue(delegateChannel);

        assertThat(settableResponses).isEmpty();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(3);
        // scheduleNextTask is attempted twice
        assertThat(settableResponses.get(1)).isEmpty();
        assertThat(settableResponses.get(2)).isEmpty();

        // interactions with the delegate are no longer rejected
        hasCapacity.set(true);
        // submit another request which triggers processing of the queue
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(5);
        assertThat(settableResponses.get(3)).isPresent();
        assertThat(settableResponses.get(4)).isPresent();
    }

    @Test
    public void testQueuedRequestExecutedOnNextSubmission_throws() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        AtomicBoolean delegateThrows = new AtomicBoolean(false);
        LimitedChannel delegateChannel = (_endpoint, _request, limitEnforcement) -> {
            if (delegateThrows.get()) {
                throw new NullPointerException("expected");
            }
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (!limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannel queued = createQueue(delegateChannel);

        assertThat(settableResponses).isEmpty();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        Optional<ListenableFuture<Response>> queuedFuture =
                queued.maybeExecute(TestEndpoint.GET, Request.builder().build());
        assertThat(queuedFuture).isPresent();
        assertThat(settableResponses).hasSize(3);

        delegateThrows.set(true);
        queued.schedule();
        assertThat(queuedFuture)
                .hasValueSatisfying(item -> assertThat(item)
                        .failsWithin(Duration.ZERO)
                        .withThrowableThat()
                        .havingRootCause()
                        .isInstanceOf(NullPointerException.class)
                        .withMessage("expected"));
    }

    @Test
    public void testQueuedRequestExecutedWhenRunningRequestCompletes() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        AtomicBoolean hasCapacity = new AtomicBoolean(false);
        LimitedChannel delegateChannel = (_endpoint, _request, limitEnforcement) -> {
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (hasCapacity.get() || !limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannel queued = createQueue(delegateChannel);

        assertThat(settableResponses).isEmpty();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(3);
        // scheduleNextTask is attempted twice
        assertThat(settableResponses.get(1)).isEmpty();
        assertThat(settableResponses.get(2)).isEmpty();

        // interactions with the delegate are no longer rejected
        // We complete the initial request, which triggers processing of the queue
        hasCapacity.set(true);
        settableResponses.get(0).get().set(new TestResponse().code(200));
        assertThat(settableResponses).hasSize(4);
        assertThat(settableResponses.get(3)).isPresent();
    }

    @Test
    @TestTracing(snapshot = true)
    public void testQueueTracing() {
        testQueuedRequestExecutedWhenRunningRequestCompletes();
    }

    @Test
    public void testQueueFullReturnsLimited() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        LimitedChannel delegateChannel = (_endpoint, _request, limitEnforcement) -> {
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (!limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannel queued = createQueue(delegateChannel, 1);

        assertThat(settableResponses).isEmpty();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        // Now that we have a request in flight, we can queue one:
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .isPresent();

        // The next request exceeds the maximum queue size
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .as("When the queue is full, this should return a 'limited' empty optional")
                .isEmpty();
    }

    @Test
    public void testQueueSizeMetric() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        AtomicBoolean hasCapacity = new AtomicBoolean(false);
        LimitedChannel delegateChannel = (_endpoint, _request, limitEnforcement) -> {
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (hasCapacity.get() || !limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannelInstrumentation instrumentation = QueuedChannel.channelInstrumentation(
                DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "channel");
        QueuedChannel queued = createQueue(delegateChannel, "channel", "queue-type", instrumentation, 100_000);

        assertThat(settableResponses).isEmpty();
        assertThat(instrumentation.requestsQueued().getCount()).isZero();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        assertThat(instrumentation.requestsQueued().getCount()).isZero();

        // Now that we have a request in flight, we can queue one:
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .isPresent();

        assertThat(instrumentation.requestsQueued().getCount()).isOne();
        assertThat(instrumentation.requestQueuedTime().getCount()).isZero();

        hasCapacity.set(true);
        settableResponses.get(0).get().set(new TestResponse().code(200));

        assertThat(instrumentation.requestsQueued().getCount()).isZero();
        assertThat(instrumentation.requestQueuedTime().getCount()).isOne();
        assertThat(instrumentation.requestQueuedTime().getSnapshot().getMax()).isPositive();
    }

    @Test
    public void testQueueTimeMetric_cancel() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        LimitedChannel delegateChannel = (_endpoint, _request, limitEnforcement) -> {
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (!limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannelInstrumentation instrumentation = QueuedChannel.channelInstrumentation(
                DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "channel");
        QueuedChannel queued = createQueue(delegateChannel, "channel", "queue-type", instrumentation, 100_000);

        assertThat(settableResponses).isEmpty();
        assertThat(instrumentation.requestsQueued().getCount()).isZero();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        assertThat(instrumentation.requestsQueued().getCount()).isZero();

        // Now that we have a request in flight, we can queue one:
        Optional<ListenableFuture<Response>> queuedResponse =
                queued.maybeExecute(TestEndpoint.GET, Request.builder().build());
        assertThat(queuedResponse).isPresent();

        assertThat(instrumentation.requestsQueued().getCount()).isOne();
        assertThat(instrumentation.requestQueuedTime().getCount()).isZero();

        queuedResponse.get().cancel(false);
        queued.schedule();

        assertThat(instrumentation.requestsQueued().getCount()).isZero();
        assertThat(instrumentation.requestQueuedTime().getCount()).isOne();
        assertThat(instrumentation.requestQueuedTime().getSnapshot().getMax()).isPositive();
    }

    @Test
    public void testQueuedResponseClosedOnCancel() throws Exception {
        List<Optional<SettableFuture<TestResponse>>> settableResponses = new CopyOnWriteArrayList<>();
        AtomicBoolean hasCapacity = new AtomicBoolean(false);
        AtomicReference<ListenableFuture<?>> queuedReturnedFuture = new AtomicReference<>();
        LimitedChannel delegateChannel = (_endpoint, req, limitEnforcement) -> {
            Optional<SettableFuture<TestResponse>> result = Optional.empty();
            if (hasCapacity.get() || !limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            ListenableFuture<?> queuedReturnedFutureValue = queuedReturnedFuture.get();
            if (result.isPresent()
                    && req.headerParams().containsKey("cancel-me")
                    && queuedReturnedFutureValue != null) {
                // When the above preconditions are met, we cancel the outer QueuedChannel future which delegates
                // to this response in order to ensure this response is correctly closed when a cancellation race
                // occurs.
                queuedReturnedFutureValue.cancel(true);
                result.get().set(new TestResponse().code(200));
            }
            return result.map(item -> DialogueFutures.transform(item, value -> value));
        };
        QueuedChannel queued = createQueue(delegateChannel);

        assertThat(settableResponses).isEmpty();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        // Now that we have a request in flight, we can queue one:
        Optional<ListenableFuture<Response>> queuedResponse = queued.maybeExecute(
                TestEndpoint.GET,
                Request.builder().putHeaderParams("cancel-me", "true").build());
        assertThat(queuedResponse).isPresent();
        queuedReturnedFuture.set(queuedResponse.get());

        hasCapacity.set(true);

        settableResponses.get(0).get().set(new TestResponse().code(200));

        assertThat(settableResponses).hasSize(4);
        TestResponse cancelledResponse = settableResponses.get(3).get().get(0, TimeUnit.SECONDS);
        assertThat(cancelledResponse.isClosed()).isTrue();
    }

    @Test
    public void testQueuedResponsePropagatesCancel() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        AtomicBoolean hasCapacity = new AtomicBoolean(false);
        LimitedChannel delegateChannel = (_endpoint, _request, limitEnforcement) -> {
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (hasCapacity.get() || !limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannelInstrumentation instrumentation = QueuedChannel.channelInstrumentation(
                DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "channel");
        QueuedChannel queued = createQueue(delegateChannel, "channel", "queue-type", instrumentation, 100_000);

        assertThat(settableResponses).isEmpty();
        assertThat(instrumentation.requestsQueued().getCount()).isZero();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        assertThat(instrumentation.requestsQueued().getCount()).isZero();

        // Now that we have a request in flight, we can queue one:
        Optional<ListenableFuture<Response>> queuedResponse =
                queued.maybeExecute(TestEndpoint.GET, Request.builder().build());
        assertThat(queuedResponse).isPresent();

        assertThat(instrumentation.requestsQueued().getCount()).isOne();
        assertThat(instrumentation.requestQueuedTime().getCount()).isZero();

        // allow the queued request to be processed
        hasCapacity.set(true);
        queued.schedule();
        // cancel the QueuedChannel response future
        queuedResponse.get().cancel(false);
        // The future on the other side of the system should be canceled as well
        assertThat(settableResponses).hasSize(4);
        assertThat(settableResponses.get(3))
                .hasValueSatisfying(item -> assertThat(item).isCancelled());

        assertThat(instrumentation.requestsQueued().getCount()).isZero();
        assertThat(instrumentation.requestQueuedTime().getCount()).isOne();
        assertThat(instrumentation.requestQueuedTime().getSnapshot().getMax()).isPositive();
    }

    @Test
    public void testQueuedResponseAvoidsExecutingCancelled() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        AtomicBoolean hasCapacity = new AtomicBoolean(false);
        LimitedChannel delegateChannel = (_endpoint, req, limitEnforcement) -> {
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (hasCapacity.get() || !limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            if (req.headerParams().containsKey("queued")) {
                result.ifPresent(resultFuture -> {
                    resultFuture.setException(new AssertionError(
                            "the queued request should be cancelled and never submitted after hasCapacity=true"));
                });
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannel queued = createQueue(delegateChannel);

        assertThat(settableResponses).isEmpty();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        Optional<ListenableFuture<Response>> queuedFuture = queued.maybeExecute(
                TestEndpoint.GET,
                Request.builder().putHeaderParams("queued", "true").build());

        assertThat(queuedFuture).isPresent();
        assertThat(settableResponses).hasSize(3);

        queuedFuture.get().cancel(true);
        hasCapacity.set(true);

        queued.schedule();

        assertThat(settableResponses)
                .as("The queued future is cancelled, and shouldn't be re-submitted")
                .hasSize(3);
    }

    @Test
    public void testInitialRequestIsIllegallyLimited_initialRequest() {
        // This LimitedChannel ignores the LimitEnforcement parameter, which is not allowed
        LimitedChannel delegateChannel = (_endpoint, _request, _limitEnforcement) -> Optional.empty();
        QueuedChannel queued = createQueue(delegateChannel);
        ListenableFuture<Response> response =
                queued.maybeExecute(TestEndpoint.GET, Request.builder().build()).get();
        assertThat(response)
                .failsWithin(Duration.ZERO)
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(SafeIllegalStateException.class)
                .withMessageContaining("A request which explicitly bypassed rate limits failed to execute");
    }

    @Test
    public void testInitialRequestIsIllegallyLimited_queuedRequest() {
        List<Optional<SettableFuture<Response>>> settableResponses = new CopyOnWriteArrayList<>();
        AtomicBoolean ignoreLimitEnforcement = new AtomicBoolean(false);
        LimitedChannel delegateChannel = (_endpoint, _request, limitEnforcement) -> {
            Optional<SettableFuture<Response>> result = Optional.empty();
            if (!ignoreLimitEnforcement.get() && !limitEnforcement.enforceLimits()) {
                result = Optional.of(SettableFuture.create());
            }
            settableResponses.add(result);
            return result.map(item -> item);
        };
        QueuedChannelInstrumentation instrumentation = QueuedChannel.channelInstrumentation(
                DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "channel");
        QueuedChannel queued = createQueue(delegateChannel, "channel", "queue-type", instrumentation, 100_000);

        assertThat(settableResponses).isEmpty();
        assertThat(instrumentation.requestsQueued().getCount()).isZero();

        // Initial request is expected to be allowed in all cases, to allow the queue to be processed
        assertThat(queued.maybeExecute(TestEndpoint.GET, Request.builder().build()))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(settableResponses).hasSize(1);
        assertThat(settableResponses.get(0))
                .hasValueSatisfying(item -> assertThat(item).isNotDone());

        assertThat(instrumentation.requestsQueued().getCount()).isZero();

        // Now that we have a request in flight, we can queue one:
        Optional<ListenableFuture<Response>> queuedResponse =
                queued.maybeExecute(TestEndpoint.GET, Request.builder().build());
        assertThat(queuedResponse).hasValueSatisfying(item -> assertThat(item).isNotDone());
        assertThat(instrumentation.requestsQueued().getCount()).isOne();

        assertThat(settableResponses).hasSize(3);

        ignoreLimitEnforcement.set(true);
        // Complete the ongoing request, allowing the queued request to be processed
        settableResponses.get(0).get().set(new TestResponse().code(200));

        assertThat(queuedResponse)
                .hasValueSatisfying(item -> assertThat(item)
                        .failsWithin(Duration.ZERO)
                        .withThrowableThat()
                        .havingRootCause()
                        .isInstanceOf(SafeIllegalStateException.class)
                        .withMessageContaining("A request which explicitly bypassed rate limits failed to execute"));

        assertThat(instrumentation.requestsQueued().getCount()).isZero();
    }

    @Nested
    class QueueTimeoutTests {
        private static final long QUEUE_TIMEOUT_NANOS = Duration.ofHours(10).toNanos();
        private static final String CHANNEL_NAME = "timeout-test";
        private static final int QUEUE_SIZE = 100;

        private ManualTicker ticker;
        private Request request;
        private ToggleableDelegate delegate;
        private QueuedChannel queuedChannel;
        private QueuedChannelInstrumentation instrumentation;
        private DeterministicScheduler scheduler;

        @BeforeEach
        void beforeEach() {
            ticker = new ManualTicker();
            request = Request.builder().build();
            delegate = new ToggleableDelegate();
            scheduler = new DeterministicScheduler();
            instrumentation = QueuedChannel.channelInstrumentation(
                    DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), CHANNEL_NAME);

            queuedChannel = new QueuedChannel(
                    delegate,
                    CHANNEL_NAME,
                    "channel",
                    instrumentation,
                    QUEUE_SIZE,
                    OptionalLong.of(QUEUE_TIMEOUT_NANOS),
                    ticker,
                    scheduler);
        }

        @Test
        void caller_future_is_failed_with_timeout() {
            setInFlightRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(TestEndpoint.POST, request);
            assertThat(callerFuture).isNotDone();
            assertThat(instrumentation.requestsQueued().getCount()).isEqualTo(1);

            scheduler.tick(QUEUE_TIMEOUT_NANOS, TimeUnit.NANOSECONDS);

            assertThat(callerFuture).isDone();
            assertThat(callerFuture)
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .withMessageContaining("queue timeout");
            assertThat(instrumentation.requestsQueued().getCount())
                    .as("the timeout task proactively decrements the queue size")
                    .isEqualTo(0);

            // The timed-out entry still sits in the deque, already decremented by the proactive timeout. Prove the
            // queue recovers: enqueue a fresh request behind the dead entry (size 0 -> 1), then free capacity and
            // drain. The drain must discard the timed-out entry (without double-decrementing the metric below zero) and
            // dispatch the live request, returning the metric to 0.
            ListenableFuture<Response> liveFuture =
                    queuedChannel.execute(TestEndpoint.POST, Request.builder().build());
            assertThat(liveFuture).isNotDone();
            assertThat(instrumentation.requestsQueued().getCount())
                    .as("fresh request is queued behind the dead entry")
                    .isEqualTo(1);

            delegate.setAccepting(true);
            queuedChannel.schedule();
            assertThat(instrumentation.requestsQueued().getCount())
                    .as("timed-out entry discarded and live request dispatched, leaving an empty queue")
                    .isEqualTo(0);

            // Completing the live request's wire call confirms it was actually dispatched.
            delegate.lastDispatched(); // setInFlightRequest's dispatch
            SettableFuture<Response> liveWire = delegate.lastDispatched();
            assertThat(liveWire).isNotNull();
            liveWire.set(new TestResponse().code(200));
            assertThat(liveFuture).succeedsWithin(Duration.ZERO);
        }

        @Test
        void timeout_after_dispatch_is_cancelled() {
            setInFlightRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(TestEndpoint.POST, request);
            assertThat(callerFuture).isNotDone();
            assertThat(instrumentation.requestsQueued().getCount())
                    .as("request is queued")
                    .isEqualTo(1);

            // Dispatch the queued request, which cancels its timeout task.
            delegate.setAccepting(true);
            queuedChannel.schedule();
            assertThat(instrumentation.requestsQueued().getCount())
                    .as("dispatch dequeued the request")
                    .isEqualTo(0);

            // Advance past the timeout. The cancelled task must not fire.
            scheduler.tick(QUEUE_TIMEOUT_NANOS * 2, TimeUnit.NANOSECONDS);

            assertThat(callerFuture)
                    .as("dispatch cancelled the timeout, so firing the scheduler is a no-op")
                    .isNotDone();
            assertThat(instrumentation.requestsQueued().getCount())
                    .as("queue remains empty")
                    .isEqualTo(0);
        }

        @Test
        void expiration_is_cleared_on_completion_so_a_reused_request_gets_a_fresh_budget() {
            setInFlightRequest();
            Request reused = Request.builder().build();

            // First execution: the delegate is rejecting, so the request queues and stamps an absolute expiration.
            ListenableFuture<Response> first = queuedChannel.execute(TestEndpoint.POST, reused);
            assertThat(QueueTimeoutAttachments.getExpiration(reused))
                    .as("first execution stamps the expiration at clock.read() + timeout")
                    .isEqualTo(QUEUE_TIMEOUT_NANOS);

            // Complete the first execution by dispatching it and completing its wire response.
            delegate.setAccepting(true);
            queuedChannel.schedule();
            delegate.lastDispatched(); // the setInFlightRequest dispatch
            SettableFuture<Response> wire = delegate.lastDispatched();
            wire.set(new TestResponse().code(200));
            assertThat(first).succeedsWithin(Duration.ZERO);

            assertThat(QueueTimeoutAttachments.getExpiration(reused))
                    .as("expiration is cleared on terminal completion so the request can be safely reused")
                    .isNull();

            // Reuse the same request for a second execution after the clock has advanced past the old timeout. It must
            // not inherit the stale deadline (which would make it time out immediately). It gets a new budget instead
            ticker.advance(Duration.ofNanos(QUEUE_TIMEOUT_NANOS * 2));
            delegate.setAccepting(false);
            ListenableFuture<Response> second = queuedChannel.execute(TestEndpoint.POST, reused);
            assertThat(second)
                    .as("reused request must not immediately time out from a stale deadline")
                    .isNotDone();
            assertThat(QueueTimeoutAttachments.getExpiration(reused))
                    .as("a fresh budget is stamped for the second execution")
                    .isEqualTo(QUEUE_TIMEOUT_NANOS * 3);
        }

        @Test
        void late_timeout_losing_dispatch_race_closes_wire_response() {
            // Covers the losing dispatch race. On dispatch, scheduleNextTask calls timeoutFuture.cancel(false) to stop
            // the timeout — but cancel(false) cannot stop a task that has *already begun executing* on the scheduler
            // thread. So even though the request is dispatched, an already-running timeout task can still run to
            // completion and fail the caller future. When that happens the dispatched wire response is orphaned and
            // must be closed rather than leaked. (This is why the caller future ends up failed despite the dispatch.)
            setInFlightRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(TestEndpoint.POST, request);
            assertThat(callerFuture).isNotDone();

            // Dispatch the queued request.
            delegate.setAccepting(true);
            queuedChannel.schedule();

            // Model that race by invoking the timeout task body directly, as if it had already started running before
            // the dispatch-time cancel(false) fired (so the cancel was a no-op and could not stop it).
            simulateTimeout(callerFuture);
            assertThat(callerFuture)
                    .as("the timeout task ran to completion despite the dispatch-time cancel (cancel(false) cannot stop"
                            + " an already-running task), so the caller future is failed with the timeout")
                    .isDone();

            // Pop the first request from the queue which was used to set inFlight > 0.
            delegate.lastDispatched();
            SettableFuture<Response> wireFuture = delegate.lastDispatched();
            assertThat(wireFuture).isNotNull();

            // When the wire later responds, ForwardAndSchedule must close the response because the caller future
            // was already failed, otherwise the response would leak.
            TestResponse wireResponse = new TestResponse().code(200);
            wireFuture.set(wireResponse);
            assertThat(wireResponse.isClosed())
                    .as("Late wire response must be closed when the caller future is already failed")
                    .isTrue();
        }

        @Test
        void timeout_is_noop_when_wire_completes_first() throws ExecutionException, InterruptedException {
            setInFlightRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(TestEndpoint.POST, request);

            delegate.setAccepting(true);
            queuedChannel.schedule();
            delegate.lastDispatched();
            SettableFuture<Response> wireFuture = delegate.lastDispatched();

            TestResponse wireResponse = new TestResponse().code(200);
            wireFuture.set(wireResponse);
            assertThat(callerFuture).isDone();
            assertThat(callerFuture.get().code()).isEqualTo(200);

            // Timeout fires after wire completed. This should be a no-op.
            simulateTimeout(callerFuture);

            // Assert that we get a 200 response (not a queue timeout exception).
            assertThat(callerFuture.get().code()).isEqualTo(200);
            assertThat(wireResponse.isClosed())
                    .as("Caller owns/can read the response")
                    .isFalse();
        }

        @Test
        void re_queued_entry_is_cleaned_up_after_timeout() {
            setInFlightRequest();

            ListenableFuture<Response> callerFuture = queuedChannel.execute(TestEndpoint.POST, request);
            assertThat(instrumentation.requestsQueued().getCount()).isEqualTo(1);

            // schedule() is called ->
            // The delegate rejects request ->
            // assert that the request is re-queued
            queuedChannel.schedule();
            assertThat(instrumentation.requestsQueued().getCount()).isEqualTo(1);
            assertThat(callerFuture).isNotDone();

            // Fire the scheduled timeout. Only the most recently re-queued entry has a live timeout task (each drain
            // cancels the previous one); it runs the real path, failing the caller future and proactively
            // decrementing the queue size.
            scheduler.tick(QUEUE_TIMEOUT_NANOS, TimeUnit.NANOSECONDS);

            assertThat(callerFuture).isDone();
            assertThat(callerFuture)
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .withMessageContaining("queue timeout");
            assertThat(instrumentation.requestsQueued().getCount())
                    .as("re-queued entry is cleaned up proactively when its timeout fires")
                    .isEqualTo(0);

            // The next drain pops the now-dead entry without double-counting.
            queuedChannel.schedule();
            assertThat(instrumentation.requestsQueued().getCount()).isEqualTo(0);
        }

        @Test
        void second_queue_reads_expiration_from_first_queue_and_does_not_overwrite() {
            AtomicBoolean accepting = new AtomicBoolean(false);
            LimitedChannel rejectingDelegate = (_endpoint, _request, limitEnforcement) -> {
                if (!accepting.get() && limitEnforcement.enforceLimits()) {
                    return Optional.empty();
                }
                return Optional.of(SettableFuture.create());
            };

            QueuedChannelInstrumentation sharedInstrumentation = QueuedChannel.channelInstrumentation(
                    DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "shared");

            QueuedChannel queue1 = new QueuedChannel(
                    rejectingDelegate,
                    "shared",
                    "channel",
                    sharedInstrumentation,
                    QUEUE_SIZE,
                    OptionalLong.of(QUEUE_TIMEOUT_NANOS),
                    ticker,
                    scheduler);
            QueuedChannel queue2 = new QueuedChannel(
                    rejectingDelegate,
                    "shared",
                    "endpoint",
                    sharedInstrumentation,
                    QUEUE_SIZE,
                    OptionalLong.of(QUEUE_TIMEOUT_NANOS),
                    ticker,
                    scheduler);

            // Get inFlight > 0 on both
            accepting.set(true);
            queue1.execute(TestEndpoint.POST, Request.builder().build());
            queue2.execute(TestEndpoint.POST, Request.builder().build());
            accepting.set(false);

            // No expiration before enqueue
            Request sharedRequest = Request.builder().build();
            assertThat(QueueTimeoutAttachments.getExpiration(sharedRequest)).isNull();

            // Enqueue in queue1 at ticker=0 — stamps expiration at 0 + TIMEOUT
            queue1.execute(TestEndpoint.POST, sharedRequest);
            Long expirationAfterQueue1 = QueueTimeoutAttachments.getExpiration(sharedRequest);
            assertThat(expirationAfterQueue1)
                    .as("queue1 should stamp expiration at ticker.read() + timeout")
                    .isEqualTo(QUEUE_TIMEOUT_NANOS);

            // Advance ticker to ensure that the ticker value read is after the expiration
            ticker.advance(Duration.ofDays(10));

            // Enqueue same request in queue2. The existing expiration should be read.
            queue2.execute(TestEndpoint.POST, sharedRequest);
            Long expirationAfterQueue2 = QueueTimeoutAttachments.getExpiration(sharedRequest);
            assertThat(expirationAfterQueue2)
                    .as("queue2 must use the existing expiration from queue1, not stamp a fresh one")
                    .isEqualTo(expirationAfterQueue1);
        }

        @Test
        void requeue_preserves_original_expiration() {
            setInFlightRequest();
            Request req = Request.builder().build();
            ListenableFuture<Response> callerFuture = queuedChannel.execute(TestEndpoint.POST, req);
            Long originalExpiration = QueueTimeoutAttachments.getExpiration(req);
            assertThat(originalExpiration)
                    .as("initial enqueue stamps expiration at ticker.read() + timeout")
                    .isEqualTo(QUEUE_TIMEOUT_NANOS);

            // Advance partway into the budget, then drain while the delegate still rejects: the head is popped and
            // re-queued via addTimeoutOnRequeue, which must read the existing expiration rather than re-stamping it.
            ticker.advance(Duration.ofNanos(QUEUE_TIMEOUT_NANOS / 2));
            queuedChannel.schedule();

            assertThat(callerFuture)
                    .as("request was re-queued, not dispatched or failed")
                    .isNotDone();
            assertThat(instrumentation.requestsQueued().getCount()).isEqualTo(1);
            assertThat(QueueTimeoutAttachments.getExpiration(req))
                    .as("re-queue must preserve the original expiration, not reset the queue-timeout budget")
                    .isEqualTo(originalExpiration);
        }

        @Test
        void request_with_already_expired_attachment_fails_immediately() {
            setInFlightRequest();

            // Stamp an expiration on the request, then advance the clock past it.
            Request expired = Request.builder().build();
            QueueTimeoutAttachments.setExpirationIfAbsent(expired, ticker.read() + QUEUE_TIMEOUT_NANOS);
            ticker.advance(Duration.ofNanos(QUEUE_TIMEOUT_NANOS + 1));

            // The delegate is rejecting (inFlight > 0), so the request reaches the queueing path, sees the expired
            // attachment, and fails immediately instead of being enqueued.
            ListenableFuture<Response> future = queuedChannel.execute(TestEndpoint.POST, expired);
            assertThat(future)
                    .as("expiration already passed, so the request fails on enqueue")
                    .isDone();
            assertThat(future).failsWithin(Duration.ZERO).withThrowableThat().withMessageContaining("queue timeout");
            assertThat(instrumentation.requestsQueued().getCount())
                    .as("a request that fails immediately is never counted in the queue")
                    .isEqualTo(0);
        }

        @Test
        void no_timeout_configured_requests_queue_indefinitely() {
            // Create a QueuedChannel with no timeout (OptionalLong.empty)
            ToggleableDelegate noTimeoutDelegate = new ToggleableDelegate();
            QueuedChannel noTimeoutQueue = new QueuedChannel(
                    noTimeoutDelegate,
                    "no-timeout",
                    "channel",
                    QueuedChannel.channelInstrumentation(
                            DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "no-timeout"),
                    QUEUE_SIZE,
                    OptionalLong.empty(),
                    ticker,
                    null);

            // Get inFlight > 0
            noTimeoutDelegate.setAccepting(true);
            noTimeoutQueue.execute(TestEndpoint.POST, Request.builder().build());
            noTimeoutDelegate.setAccepting(false);

            Request req = Request.builder().build();
            ListenableFuture<Response> callerFuture = noTimeoutQueue.execute(TestEndpoint.POST, req);
            assertThat(callerFuture).isNotDone();

            // No expiration should be stamped
            assertThat(QueueTimeoutAttachments.getExpiration(req)).isNull();

            // Advance time
            ticker.advance(Duration.ofHours(1));

            // Future should still be pending
            assertThat(callerFuture)
                    .as("No timeout configured, request should queue indefinitely")
                    .isNotDone();
        }

        @Test
        void drain_loop_skips_timed_out_request_and_dispatches_live_one() {
            setInFlightRequest();

            // Enqueue two requests
            Request req1 = Request.builder().build();
            Request req2 = Request.builder().build();
            ListenableFuture<Response> future1 = queuedChannel.execute(TestEndpoint.POST, req1);
            ListenableFuture<Response> future2 = queuedChannel.execute(TestEndpoint.POST, req2);
            assertThat(future1).isNotDone();
            assertThat(future2).isNotDone();
            assertThat(instrumentation.requestsQueued().getCount()).isEqualTo(2);

            // Time out only the first request
            simulateTimeout(future1);
            assertThat(future1).isDone();
            assertThat(future2).as("Second request should still be alive").isNotDone();

            // scheduleNextTask should skip future1 and dispatch future2
            delegate.setAccepting(true);
            queuedChannel.schedule();

            assertThat(instrumentation.requestsQueued().getCount()).isEqualTo(0);

            // Prove future2 was actually dispatched, not merely still queued: the first dispatched wire call belongs
            // to setInFlightRequest, the second to future2. Completing future2's wire call must flow back through
            // ForwardAndSchedule and complete future2 with the response.
            delegate.lastDispatched();
            SettableFuture<Response> future2Wire = delegate.lastDispatched();
            assertThat(future2Wire)
                    .as("future2 should have been handed to the delegate")
                    .isNotNull();
            // Use a distinctive status code so the assertion proves future2 carries *this* wire call's response,
            // not merely that it completed successfully.
            future2Wire.set(new TestResponse().code(202));
            assertThat(future2)
                    .as("future2 completes with the response from its dispatched wire call, confirming it was"
                            + " dispatched")
                    .succeedsWithin(Duration.ZERO)
                    .extracting(Response::code)
                    .isEqualTo(202);
        }

        // Gets inFlight > 0 to avoid DANGEROUS_BYPASS_LIMITS on subsequent requests
        private void setInFlightRequest() {
            delegate.setAccepting(true);
            queuedChannel.execute(TestEndpoint.POST, request);
            delegate.setAccepting(false);
        }

        // Directly invokes the timeout action on a specific queued request, modelling the timeout task body running.
        // A fresh, uncounted QueueSizeAccounting is passed so the decrement is a no-op here; the real entry's queue
        // size is reconciled by the subsequent drain in these tests.
        private void simulateTimeout(ListenableFuture<Response> future) {
            queuedChannel.failWithQueueTimeout(
                    (SettableFuture<Response>) future,
                    DetachedSpan.start("test"),
                    new QueuedChannel.IdempotentTimerContext(DialogueClientMetrics.of(new DefaultTaggedMetricRegistry())
                            .requestQueuedTime("test")
                            .time()),
                    queuedChannel.new QueueSizeAccounting());
        }

        // Ticker whose time only advances when explicitly told to.
        static final class ManualTicker implements Ticker {
            private long nanos;

            @Override
            public long read() {
                return nanos;
            }

            void advance(Duration duration) {
                nanos += duration.toNanos();
            }
        }

        // A {@link LimitedChannel} that can be toggled between accepting and rejecting.
        static final class ToggleableDelegate implements LimitedChannel {
            private volatile boolean accepting;
            private final Queue<SettableFuture<Response>> dispatched = new ConcurrentLinkedQueue<>();

            void setAccepting(boolean value) {
                this.accepting = value;
            }

            SettableFuture<Response> lastDispatched() {
                return dispatched.poll();
            }

            @Override
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
    }
}
