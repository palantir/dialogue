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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.QueuedChannel.QueuedChannelInstrumentation;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tracing.TestTracing;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
        return new QueuedChannel(
                delegate,
                channelName,
                "queue-type",
                QueuedChannel.channelInstrumentation(
                        DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), channelName),
                maxQueueSize);
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
        QueuedChannel queued = new QueuedChannel(delegateChannel, "channel", "queue-type", instrumentation, 100_000);

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
        QueuedChannel queued = new QueuedChannel(delegateChannel, "channel", "queue-type", instrumentation, 100_000);

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
        QueuedChannel queued = new QueuedChannel(delegateChannel, "channel", "queue-type", instrumentation, 100_000);

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
        QueuedChannel queued = new QueuedChannel(delegateChannel, "channel", "queue-type", instrumentation, 100_000);

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
}
