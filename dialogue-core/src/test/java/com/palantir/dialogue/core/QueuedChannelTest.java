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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.dialogue.core.QueuedChannel.QueuedChannelInstrumentation;
import com.palantir.tracing.TestTracing;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("FutureReturnValueIgnored")
public class QueuedChannelTest {

    private static final LimitEnforcement DO_NOT_SKIP_LIMITS = LimitEnforcement.DEFAULT_ENABLED;

    @Mock
    private LimitedChannel delegate;

    @Mock
    private Endpoint endpoint;

    @Mock
    private Request request;

    @Mock
    private Response mockResponse;

    private QueuedChannel queuedChannel;
    private SettableFuture<Response> futureResponse;
    private Optional<ListenableFuture<Response>> maybeResponse;

    @BeforeEach
    public void before() {
        queuedChannel = new QueuedChannel(
                delegate,
                "my-channel",
                "queue-type",
                QueuedChannel.channelInstrumentation(
                        DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "my-channel"),
                100_000);
        futureResponse = SettableFuture.create();
        maybeResponse = Optional.of(futureResponse);

        mockHasCapacity();
    }

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
    public void testQueuedRequestExecutedOnNextSubmission_throws() throws ExecutionException, InterruptedException {
        // First request is limited by the channel and queued
        Request queuedRequest = Mockito.mock(Request.class);
        when(delegate.maybeExecute(endpoint, queuedRequest, DO_NOT_SKIP_LIMITS)).thenReturn(Optional.empty());
        ListenableFuture<Response> queuedFuture =
                queuedChannel.maybeExecute(endpoint, queuedRequest).get();
        verify(delegate, times(2)).maybeExecute(endpoint, queuedRequest, DO_NOT_SKIP_LIMITS);
        assertThat(queuedFuture).isNotDone();

        // Second request succeeds and the queued request is attempted, but throws an exception
        futureResponse.set(mockResponse);
        when(delegate.maybeExecute(endpoint, request, DO_NOT_SKIP_LIMITS)).thenReturn(maybeResponse);
        when(delegate.maybeExecute(endpoint, queuedRequest, DO_NOT_SKIP_LIMITS))
                .thenThrow(new NullPointerException("expected"));
        ListenableFuture<Response> completed =
                queuedChannel.maybeExecute(endpoint, request).get();
        // Both results should be completed. The thrown exception should
        // be converted into a failed future by NeverThrowLimitedChannel
        assertThat(completed).isDone();
        assertThat(queuedFuture).isDone();
        assertThat(completed.get()).isEqualTo(mockResponse);
        assertThatThrownBy(queuedFuture::get).hasRootCauseMessage("expected");
        verify(delegate, times(1)).maybeExecute(endpoint, request, DO_NOT_SKIP_LIMITS);
        verify(delegate, times(3)).maybeExecute(endpoint, queuedRequest, DO_NOT_SKIP_LIMITS);
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
    public void testQueuedResponseClosedOnCancel() {
        Request queuedRequest =
                Request.builder().pathParams(ImmutableMap.of("foo", "bar")).build();
        when(delegate.maybeExecute(endpoint, queuedRequest, DO_NOT_SKIP_LIMITS)).thenReturn(Optional.empty());
        ListenableFuture<Response> result =
                queuedChannel.maybeExecute(endpoint, queuedRequest).get();
        verify(delegate, times(2)).maybeExecute(endpoint, queuedRequest, DO_NOT_SKIP_LIMITS);

        when(delegate.maybeExecute(endpoint, request, DO_NOT_SKIP_LIMITS))
                .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
        when(delegate.maybeExecute(endpoint, queuedRequest, DO_NOT_SKIP_LIMITS))
                .thenAnswer((Answer<Optional<ListenableFuture<Response>>>) _invocation -> {
                    // cancel from this invocation to simulate the race between cancellation and execution
                    assertThat(result.cancel(true)).isTrue();
                    return Optional.of(Futures.immediateFuture(mockResponse));
                });
        // Force scheduling
        queuedChannel.maybeExecute(endpoint, request);
        assertThat(result).isCancelled();
        verify(delegate, times(1)).maybeExecute(endpoint, request, DO_NOT_SKIP_LIMITS);
        verify(mockResponse, times(1)).close();
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
        Request queued = Request.builder().putHeaderParams("key", "val").build();
        when(delegate.maybeExecute(endpoint, queued, DO_NOT_SKIP_LIMITS)).thenReturn(Optional.empty());
        ListenableFuture<Response> result =
                queuedChannel.maybeExecute(endpoint, queued).get();
        verify(delegate, times(2)).maybeExecute(endpoint, queued, DO_NOT_SKIP_LIMITS);

        assertThat(result.cancel(true)).isTrue();
        when(delegate.maybeExecute(endpoint, request, DO_NOT_SKIP_LIMITS))
                .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
        queuedChannel.maybeExecute(endpoint, request);
        verify(delegate, times(1)).maybeExecute(endpoint, request, DO_NOT_SKIP_LIMITS);
        // Should not have been invoked any more.
        verify(delegate, times(2)).maybeExecute(endpoint, queued, DO_NOT_SKIP_LIMITS);
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockHasCapacity() {
        return Mockito.lenient()
                .when(delegate.maybeExecute(endpoint, request, DO_NOT_SKIP_LIMITS))
                .thenReturn(maybeResponse);
    }
}
