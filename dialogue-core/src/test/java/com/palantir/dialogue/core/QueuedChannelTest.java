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

import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tracing.TestTracing;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
        queuedChannel =
                new QueuedChannel(delegate, "my-channel", DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()));
        futureResponse = SettableFuture.create();
        maybeResponse = Optional.of(futureResponse);

        mockHasCapacity();
    }

    @Test
    public void testReceivesSuccessfulResponse() throws ExecutionException, InterruptedException {
        ListenableFuture<Response> response =
                queuedChannel.maybeExecute(endpoint, request).get();
        assertThat(response.isDone()).isFalse();

        futureResponse.set(mockResponse);

        assertThat(response.isDone()).isTrue();
        assertThat(response.get()).isEqualTo(mockResponse);
    }

    @Test
    public void testReceivesExceptionalResponse() {
        ListenableFuture<Response> response =
                queuedChannel.maybeExecute(endpoint, request).get();
        assertThat(response.isDone()).isFalse();

        futureResponse.setException(new IllegalArgumentException());

        assertThat(response.isDone()).isTrue();
        assertThatThrownBy(() -> response.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testQueuedRequestExecutedOnNextSubmission() {
        mockNoCapacity();
        queuedChannel.maybeExecute(endpoint, request);
        verify(delegate, times(2)).maybeExecute(endpoint, request);

        mockHasCapacity();
        queuedChannel.maybeExecute(endpoint, request);
        verify(delegate, times(4)).maybeExecute(endpoint, request);
    }

    @Test
    public void testQueuedRequestExecutedOnNextSubmission_throws() throws ExecutionException, InterruptedException {
        // First request is limited by the channel and queued
        Request queuedRequest = Mockito.mock(Request.class);
        when(delegate.maybeExecute(endpoint, queuedRequest)).thenReturn(Optional.empty());
        ListenableFuture<Response> queuedFuture =
                queuedChannel.maybeExecute(endpoint, queuedRequest).get();
        verify(delegate, times(2)).maybeExecute(endpoint, queuedRequest);
        assertThat(queuedFuture).isNotDone();

        // Second request succeeds and the queued request is attempted, but throws an exception
        futureResponse.set(mockResponse);
        when(delegate.maybeExecute(endpoint, request)).thenReturn(maybeResponse);
        when(delegate.maybeExecute(endpoint, queuedRequest)).thenThrow(new NullPointerException("expected"));
        ListenableFuture<Response> completed =
                queuedChannel.maybeExecute(endpoint, request).get();
        // Both results should be completed. The thrown exception should
        // be converted into a failed future by NeverThrowLimitedChannel
        assertThat(completed).isDone();
        assertThat(queuedFuture).isDone();
        assertThat(completed.get()).isEqualTo(mockResponse);
        assertThatThrownBy(queuedFuture::get).hasRootCauseMessage("expected");
        verify(delegate, times(1)).maybeExecute(endpoint, request);
        verify(delegate, times(3)).maybeExecute(endpoint, queuedRequest);
    }

    @Test
    public void testQueuedRequestExecutedWhenRunningRequestCompletes() {
        mockHasCapacity();
        queuedChannel.maybeExecute(endpoint, request);
        verify(delegate, times(1)).maybeExecute(endpoint, request);

        mockNoCapacity();
        queuedChannel.maybeExecute(endpoint, request);
        verify(delegate, times(3)).maybeExecute(endpoint, request);
        futureResponse.set(mockResponse);

        verify(delegate, times(4)).maybeExecute(endpoint, request);
    }

    @Test
    @TestTracing(snapshot = true)
    public void testQueueTracing() {
        // Put requests on queue
        mockNoCapacity();
        queuedChannel.maybeExecute(endpoint, request);
        queuedChannel.maybeExecute(endpoint, request);
        verify(delegate, times(3)).maybeExecute(endpoint, request);

        // flush queue by completing a request
        mockHasCapacity();
        queuedChannel.maybeExecute(endpoint, request);
        verify(delegate, times(6)).maybeExecute(endpoint, request);
        futureResponse.set(mockResponse);

        verify(delegate, times(6)).maybeExecute(endpoint, request);
    }

    @Test
    public void testQueueFullReturnsLimited() {
        queuedChannel = new QueuedChannel(
                delegate, "my-channel", DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), 1);

        mockNoCapacity();
        queuedChannel.maybeExecute(endpoint, request);

        assertThat(queuedChannel.maybeExecute(endpoint, request)).isEmpty();
    }

    @Test
    public void testQueueSizeMetric() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        String channelName = "my-channel";

        queuedChannel = new QueuedChannel(delegate, "my-channel", metrics, 1);

        mockNoCapacity();
        queuedChannel.maybeExecute(endpoint, request);

        assertThat(queuedChannel.maybeExecute(endpoint, request)).isEmpty();
        assertThat(metrics.requestsQueued(channelName).getCount()).isOne();
    }

    @Test
    public void testQueueTimeMetric_success() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        String channelName = "my-channel";

        queuedChannel = new QueuedChannel(delegate, "my-channel", metrics, 1);

        mockNoCapacity();
        assertThat(queuedChannel.maybeExecute(endpoint, request))
                .hasValueSatisfying(future -> assertThat(future).isNotDone());
        mockHasCapacity();
        queuedChannel.schedule();
        futureResponse.set(mockResponse);

        Timer timer = metrics.requestQueuedTime(channelName);
        assertThat(timer.getCount()).isOne();
        assertThat(timer.getSnapshot().getMax()).isPositive();
    }

    @Test
    public void testQueueTimeMetric_cancel() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        String channelName = "my-channel";

        queuedChannel = new QueuedChannel(delegate, "my-channel", metrics, 1);

        mockNoCapacity();
        Optional<ListenableFuture<Response>> result = queuedChannel.maybeExecute(endpoint, request);
        assertThat(result).hasValueSatisfying(future -> assertThat(future).isNotDone());
        result.get().cancel(true);
        queuedChannel.schedule();

        Timer timer = metrics.requestQueuedTime(channelName);
        assertThat(timer.getCount()).isOne();
        assertThat(timer.getSnapshot().getMax()).isPositive();
    }

    @Test
    public void testQueuedResponseClosedOnCancel() {
        Request queuedRequest =
                Request.builder().pathParams(ImmutableMap.of("foo", "bar")).build();
        when(delegate.maybeExecute(endpoint, queuedRequest)).thenReturn(Optional.empty());
        ListenableFuture<Response> result =
                queuedChannel.maybeExecute(endpoint, queuedRequest).get();
        verify(delegate, times(2)).maybeExecute(endpoint, queuedRequest);

        when(delegate.maybeExecute(endpoint, request))
                .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
        when(delegate.maybeExecute(endpoint, queuedRequest))
                .thenAnswer((Answer<Optional<ListenableFuture<Response>>>) _invocation -> {
                    // cancel from this invocation to simulate the race between cancellation and execution
                    assertThat(result.cancel(true)).isTrue();
                    return Optional.of(Futures.immediateFuture(mockResponse));
                });
        // Force scheduling
        queuedChannel.maybeExecute(endpoint, request);
        assertThat(result).isCancelled();
        verify(delegate, times(1)).maybeExecute(endpoint, request);
        verify(mockResponse, times(1)).close();
    }

    @Test
    public void testQueuedResponsePropagatesCancel() {
        Request queued = Request.builder().putHeaderParams("key", "val").build();
        when(delegate.maybeExecute(endpoint, queued)).thenReturn(Optional.empty());
        ListenableFuture<Response> result =
                queuedChannel.maybeExecute(endpoint, queued).get();
        verify(delegate, times(2)).maybeExecute(endpoint, queued);

        when(delegate.maybeExecute(endpoint, request))
                .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
        when(delegate.maybeExecute(endpoint, queued)).thenReturn(maybeResponse);
        queuedChannel.maybeExecute(endpoint, request);
        result.cancel(true);
        assertThat(futureResponse).isCancelled();
        verify(delegate, times(1)).maybeExecute(endpoint, request);
        verify(delegate, times(3)).maybeExecute(endpoint, queued);
    }

    @Test
    public void testQueuedResponseAvoidsExecutingCancelled() {
        Request queued = Request.builder().putHeaderParams("key", "val").build();
        when(delegate.maybeExecute(endpoint, queued)).thenReturn(Optional.empty());
        ListenableFuture<Response> result =
                queuedChannel.maybeExecute(endpoint, queued).get();
        verify(delegate, times(2)).maybeExecute(endpoint, queued);

        assertThat(result.cancel(true)).isTrue();
        when(delegate.maybeExecute(endpoint, request))
                .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
        queuedChannel.maybeExecute(endpoint, request);
        verify(delegate, times(1)).maybeExecute(endpoint, request);
        // Should not have been invoked any more.
        verify(delegate, times(2)).maybeExecute(endpoint, queued);
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockHasCapacity() {
        return when(delegate.maybeExecute(endpoint, request)).thenReturn(maybeResponse);
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockNoCapacity() {
        return when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.empty());
    }
}
