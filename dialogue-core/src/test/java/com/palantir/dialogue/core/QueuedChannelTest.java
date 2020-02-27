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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Response;
import com.palantir.tracing.TestTracing;
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
    private LimitedRequest request;

    @Mock
    private Response mockResponse;

    private QueuedChannel queuedChannel;
    private SettableFuture<Response> futureResponse;
    private Optional<ListenableFuture<Response>> maybeResponse;

    @BeforeEach
    public void before() {
        queuedChannel = new QueuedChannel(delegate);
        futureResponse = SettableFuture.create();
        maybeResponse = Optional.of(futureResponse);

        mockHasCapacity();
    }

    @Test
    public void testReceivesSuccessfulResponse() throws ExecutionException, InterruptedException {
        ListenableFuture<Response> response =
                queuedChannel.maybeExecute(request).get();
        assertThat(response.isDone()).isFalse();

        futureResponse.set(mockResponse);

        assertThat(response.isDone()).isTrue();
        assertThat(response.get()).isEqualTo(mockResponse);
    }

    @Test
    public void testReceivesExceptionalResponse() {
        ListenableFuture<Response> response =
                queuedChannel.maybeExecute(request).get();
        assertThat(response.isDone()).isFalse();

        futureResponse.setException(new IllegalArgumentException());

        assertThat(response.isDone()).isTrue();
        assertThatThrownBy(() -> response.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueuedRequestExecutedOnNextSubmission() {
        mockNoCapacity();
        queuedChannel.maybeExecute(request);
        verify(delegate, times(2)).maybeExecute(request);

        mockHasCapacity();
        queuedChannel.maybeExecute(request);
        verify(delegate, times(4)).maybeExecute(request);
    }

    @Test
    public void testQueuedRequestExecutedOnNextSubmission_throws() throws ExecutionException, InterruptedException {
        // First request is limited by the channel and queued
        LimitedRequest queuedRequest = Mockito.mock(LimitedRequest.class);
        when(delegate.maybeExecute(queuedRequest)).thenReturn(Optional.empty());
        ListenableFuture<Response> queuedFuture =
                queuedChannel.maybeExecute(queuedRequest).get();
        verify(delegate, times(2)).maybeExecute(queuedRequest);
        assertThat(queuedFuture).isNotDone();

        // Second request succeeds and the queued request is attempted, but throws an exception
        futureResponse.set(mockResponse);
        when(delegate.maybeExecute(request)).thenReturn(maybeResponse);
        when(delegate.maybeExecute(queuedRequest)).thenThrow(new NullPointerException("expected"));
        ListenableFuture<Response> completed =
                queuedChannel.maybeExecute(request).get();
        // Both results should be completed. The thrown exception should
        // be converted into a failed future by NeverThrowLimitedChannel
        assertThat(completed).isDone();
        assertThat(queuedFuture).isDone();
        assertThat(completed.get()).isEqualTo(mockResponse);
        assertThatThrownBy(queuedFuture::get).hasRootCauseMessage("expected");
        verify(delegate, times(1)).maybeExecute(request);
        verify(delegate, times(3)).maybeExecute(queuedRequest);
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueuedRequestExecutedWhenRunningRequestCompletes() {
        mockHasCapacity();
        queuedChannel.maybeExecute(request);
        verify(delegate, times(1)).maybeExecute(request);

        mockNoCapacity();
        queuedChannel.maybeExecute(request);
        verify(delegate, times(3)).maybeExecute(request);
        futureResponse.set(mockResponse);

        verify(delegate, times(4)).maybeExecute(request);
    }

    @Test
    @TestTracing(snapshot = true)
    public void testQueueTracing() {
        // Put requests on queue
        mockNoCapacity();
        queuedChannel.maybeExecute(request);
        queuedChannel.maybeExecute(request);
        verify(delegate, times(3)).maybeExecute(request);

        // flush queue by completing a request
        mockHasCapacity();
        queuedChannel.maybeExecute(request);
        verify(delegate, times(6)).maybeExecute(request);
        futureResponse.set(mockResponse);

        verify(delegate, times(6)).maybeExecute(request);
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueueFullReturnsLimited() {
        queuedChannel = new QueuedChannel(delegate, 1);

        mockNoCapacity();
        queuedChannel.maybeExecute(request);

        assertThat(queuedChannel.maybeExecute(request)).isEmpty();
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueuedResponseClosedOnCancel() {
        LimitedRequest queuedRequest = _channel -> Futures.immediateFailedFuture(new RuntimeException());
        when(delegate.maybeExecute(queuedRequest)).thenReturn(Optional.empty());
        ListenableFuture<Response> result =
                queuedChannel.maybeExecute(queuedRequest).get();
        verify(delegate, times(2)).maybeExecute(queuedRequest);

        when(delegate.maybeExecute(request))
                .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
        when(delegate.maybeExecute(queuedRequest))
                .thenAnswer((Answer<Optional<ListenableFuture<Response>>>) _invocation -> {
                    // cancel from this invocation to simulate the race between cancellation and execution
                    assertThat(result.cancel(true)).isTrue();
                    return Optional.of(Futures.immediateFuture(mockResponse));
                });
        // Force scheduling
        queuedChannel.maybeExecute(request);
        assertThat(result).isCancelled();
        verify(delegate, times(1)).maybeExecute(request);
        verify(mockResponse, times(1)).close();
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueuedResponsePropagatesCancel() {
        LimitedRequest queued = _channel -> Futures.immediateFailedFuture(new RuntimeException());
        when(delegate.maybeExecute(queued)).thenReturn(Optional.empty());
        ListenableFuture<Response> result = queuedChannel.maybeExecute(queued).get();
        verify(delegate, times(2)).maybeExecute(queued);

        when(delegate.maybeExecute(request))
                .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
        when(delegate.maybeExecute(queued)).thenReturn(maybeResponse);
        queuedChannel.maybeExecute(request);
        result.cancel(true);
        assertThat(futureResponse).isCancelled();
        verify(delegate, times(1)).maybeExecute(request);
        verify(delegate, times(3)).maybeExecute(queued);
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueuedResponseAvoidsExecutingCancelled() {
        LimitedRequest queued = _channel -> Futures.immediateFailedFuture(new RuntimeException());
        when(delegate.maybeExecute(queued)).thenReturn(Optional.empty());
        ListenableFuture<Response> result = queuedChannel.maybeExecute(queued).get();
        verify(delegate, times(2)).maybeExecute(queued);

        assertThat(result.cancel(true)).isTrue();
        when(delegate.maybeExecute(request))
                .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
        queuedChannel.maybeExecute(request);
        verify(delegate, times(1)).maybeExecute(request);
        // Should not have been invoked any more.
        verify(delegate, times(2)).maybeExecute(queued);
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockHasCapacity() {
        return when(delegate.maybeExecute(request)).thenReturn(maybeResponse);
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockNoCapacity() {
        return when(delegate.maybeExecute(request)).thenReturn(Optional.empty());
    }
}
