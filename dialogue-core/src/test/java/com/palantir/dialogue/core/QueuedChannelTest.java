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

import com.codahale.metrics.Gauge;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("FutureReturnValueIgnored")
public class QueuedChannelTest {

    private static final MetricName NUM_QUEUED_METRIC = MetricName.builder()
            .safeName("com.palantir.conjure.java.dispatcher.calls.queued")
            .build();
    private static final MetricName NUM_RUNNING_METRICS = MetricName.builder()
            .safeName("com.palantir.conjure.java.dispatcher.calls.running")
            .build();

    @Mock
    private LimitedChannel delegate;

    @Mock
    private Endpoint endpoint;

    @Mock
    private Request request;

    @Mock
    private Response mockResponse;

    private TaggedMetricRegistry metrics;
    private QueuedChannel queuedChannel;
    private SettableFuture<Response> futureResponse;
    private Optional<ListenableFuture<Response>> maybeResponse;

    @BeforeEach
    public void before() {
        metrics = new DefaultTaggedMetricRegistry();
        queuedChannel = new QueuedChannel(delegate, DispatcherMetrics.of(metrics));
        futureResponse = SettableFuture.create();
        maybeResponse = Optional.of(futureResponse);

        mockHasCapacity();
    }

    @Test
    public void testReceivesSuccessfulResponse() throws ExecutionException, InterruptedException {
        ListenableFuture<Response> response = queuedChannel.execute(endpoint, request);
        assertThat(response.isDone()).isFalse();

        futureResponse.set(mockResponse);

        assertThat(response.isDone()).isTrue();
        assertThat(response.get()).isEqualTo(mockResponse);
    }

    @Test
    public void testReceivesExceptionalResponse() {
        ListenableFuture<Response> response = queuedChannel.execute(endpoint, request);
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
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(2)).maybeExecute(endpoint, request);

        mockHasCapacity();
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(3)).maybeExecute(endpoint, request);
    }

    @Test
    public void testQueuedRequestExecutedOnNextSubmission_throws() throws ExecutionException, InterruptedException {
        // First request is limited by the channel and queued
        Request queuedRequest = Mockito.mock(Request.class);
        when(delegate.maybeExecute(endpoint, queuedRequest)).thenReturn(Optional.empty());
        ListenableFuture<Response> queuedFuture = queuedChannel.execute(endpoint, queuedRequest);
        verify(delegate, times(2)).maybeExecute(endpoint, queuedRequest);
        assertThat(queuedFuture).isNotDone();

        // Second request succeeds and the queued request is attempted, but throws an exception
        futureResponse.set(mockResponse);
        when(delegate.maybeExecute(endpoint, request)).thenReturn(maybeResponse);
        when(delegate.maybeExecute(endpoint, queuedRequest)).thenThrow(new NullPointerException("expected"));
        ListenableFuture<Response> completed = queuedChannel.execute(endpoint, request);
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
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueuedRequestExecutedWhenRunningRequestCompletes() {
        mockHasCapacity();
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(1)).maybeExecute(endpoint, request);

        mockNoCapacity();
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(3)).maybeExecute(endpoint, request);

        futureResponse.set(mockResponse);

        verify(delegate, times(4)).maybeExecute(endpoint, request);
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueueFullReturns429() throws ExecutionException, InterruptedException {
        queuedChannel = new QueuedChannel(delegate, 1, DispatcherMetrics.of(metrics));

        mockNoCapacity();
        queuedChannel.execute(endpoint, request);

        assertThat(queuedChannel.execute(endpoint, request).get().code()).isEqualTo(429);
    }

    @Test
    public void emitsQueueCapacityMetrics_whenChannelsHasNoCapacity() {
        mockNoCapacity();

        queuedChannel.execute(endpoint, request);
        assertThat(gaugeValue(NUM_QUEUED_METRIC)).isEqualTo(1);
        assertThat(gaugeValue(NUM_RUNNING_METRICS)).isZero();

        queuedChannel.execute(endpoint, request);
        assertThat(gaugeValue(NUM_QUEUED_METRIC)).isEqualTo(2);
        assertThat(gaugeValue(NUM_RUNNING_METRICS)).isZero();
    }

    @Test
    public void emitsQueueCapacityMetrics_whenChannelHasCapacity() {
        mockHasCapacity();

        queuedChannel.execute(endpoint, request);
        assertThat(gaugeValue(NUM_QUEUED_METRIC)).isZero();
        assertThat(gaugeValue(NUM_RUNNING_METRICS)).isEqualTo(1);

        futureResponse.set(mockResponse);
        assertThat(gaugeValue(NUM_QUEUED_METRIC)).isZero();
        assertThat(gaugeValue(NUM_RUNNING_METRICS)).isZero();
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testQueuedResponseClosedOnCancel() {
        mockNoCapacity();
        ListenableFuture<Response> result = queuedChannel.execute(endpoint, request);
        verify(delegate, times(2)).maybeExecute(endpoint, request);

        result.cancel(true);
        mockHasCapacity();
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(3)).maybeExecute(endpoint, request);
        futureResponse.set(mockResponse);
        verify(mockResponse, times(1)).close();
    }

    @SuppressWarnings("unchecked")
    private Integer gaugeValue(MetricName metric) {
        return ((Gauge<Integer>) metrics.getMetrics().get(metric)).getValue();
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockHasCapacity() {
        return when(delegate.maybeExecute(endpoint, request)).thenReturn(maybeResponse);
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockNoCapacity() {
        return when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.empty());
    }
}
