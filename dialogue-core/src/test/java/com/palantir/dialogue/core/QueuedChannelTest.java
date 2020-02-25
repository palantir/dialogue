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

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("FutureReturnValueIgnored")
public class QueuedChannelTest {
    // private static final MetricName NUM_QUEUED_METRIC = MetricName.builder()
    //         .safeName("com.palantir.conjure.java.dispatcher.calls.queued")
    //         .build();
    // private static final MetricName NUM_RUNNING_METRICS = MetricName.builder()
    //         .safeName("com.palantir.conjure.java.dispatcher.calls.running")
    //         .build();
    //
    // @Mock
    // private LimitedChannel delegate;
    //
    // @Mock
    // private Endpoint endpoint;
    //
    // @Mock
    // private Request request;
    //
    // @Mock
    // private Response mockResponse;
    //
    // private TaggedMetricRegistry metrics;
    // private QueuedChannel queuedChannel;
    // private SettableFuture<Response> futureResponse;
    // private Optional<ListenableFuture<Response>> maybeResponse;
    //
    // @BeforeEach
    // public void before() {
    //     metrics = new DefaultTaggedMetricRegistry();
    //     queuedChannel = new QueuedChannel(delegate, DispatcherMetrics.of(metrics));
    //     futureResponse = SettableFuture.create();
    //     maybeResponse = Optional.of(futureResponse);
    //
    //     mockHasCapacity();
    // }
    //
    // @Test
    // public void testReceivesSuccessfulResponse() throws ExecutionException, InterruptedException {
    //     ListenableFuture<Response> response = queuedChannel.execute(endpoint, request);
    //     assertThat(response.isDone()).isFalse();
    //
    //     futureResponse.set(mockResponse);
    //
    //     assertThat(response.isDone()).isTrue();
    //     assertThat(response.get()).isEqualTo(mockResponse);
    // }
    //
    // @Test
    // public void testReceivesExceptionalResponse() {
    //     ListenableFuture<Response> response = queuedChannel.execute(endpoint, request);
    //     assertThat(response.isDone()).isFalse();
    //
    //     futureResponse.setException(new IllegalArgumentException());
    //
    //     assertThat(response.isDone()).isTrue();
    //     assertThatThrownBy(() -> response.get())
    //             .isInstanceOf(ExecutionException.class)
    //             .hasCauseInstanceOf(IllegalArgumentException.class);
    // }
    //
    // @Test
    // @SuppressWarnings("FutureReturnValueIgnored")
    // public void testQueuedRequestExecutedOnNextSubmission() {
    //     mockNoCapacity();
    //     queuedChannel.execute(endpoint, request);
    //     verify(delegate, times(2)).maybeExecute(endpoint, request);
    //
    //     mockHasCapacity();
    //     queuedChannel.execute(endpoint, request);
    //     verify(delegate, times(3)).maybeExecute(endpoint, request);
    // }
    //
    // @Test
    // public void testQueuedRequestExecutedOnNextSubmission_throws() throws ExecutionException, InterruptedException {
    //     // First request is limited by the channel and queued
    //     Request queuedRequest = Mockito.mock(Request.class);
    //     when(delegate.maybeExecute(endpoint, queuedRequest)).thenReturn(Optional.empty());
    //     ListenableFuture<Response> queuedFuture = queuedChannel.execute(endpoint, queuedRequest);
    //     verify(delegate, times(2)).maybeExecute(endpoint, queuedRequest);
    //     assertThat(queuedFuture).isNotDone();
    //
    //     // Second request succeeds and the queued request is attempted, but throws an exception
    //     futureResponse.set(mockResponse);
    //     when(delegate.maybeExecute(endpoint, request)).thenReturn(maybeResponse);
    //     when(delegate.maybeExecute(endpoint, queuedRequest)).thenThrow(new NullPointerException("expected"));
    //     ListenableFuture<Response> completed = queuedChannel.execute(endpoint, request);
    //     // Both results should be completed. The thrown exception should
    //     // be converted into a failed future by NeverThrowLimitedChannel
    //     assertThat(completed).isDone();
    //     assertThat(queuedFuture).isDone();
    //     assertThat(completed.get()).isEqualTo(mockResponse);
    //     assertThatThrownBy(queuedFuture::get).hasRootCauseMessage("expected");
    //     verify(delegate, times(1)).maybeExecute(endpoint, request);
    //     verify(delegate, times(3)).maybeExecute(endpoint, queuedRequest);
    // }
    //
    // @Test
    // @SuppressWarnings("FutureReturnValueIgnored")
    // public void testQueuedRequestExecutedWhenRunningRequestCompletes() {
    //     mockHasCapacity();
    //     queuedChannel.execute(endpoint, request);
    //     verify(delegate, times(1)).maybeExecute(endpoint, request);
    //
    //     mockNoCapacity();
    //     queuedChannel.execute(endpoint, request);
    //     verify(delegate, times(3)).maybeExecute(endpoint, request);
    //     futureResponse.set(mockResponse);
    //
    //     verify(delegate, times(4)).maybeExecute(endpoint, request);
    // }
    //
    // @Test
    // @TestTracing(snapshot = true)
    // public void testQueueTracing() {
    //     // Put requests on queue
    //     mockNoCapacity();
    //     queuedChannel.execute(endpoint, request);
    //     queuedChannel.execute(endpoint, request);
    //     verify(delegate, times(4)).maybeExecute(endpoint, request);
    //
    //     // flush queue by completing a request
    //     mockHasCapacity();
    //     queuedChannel.execute(endpoint, request);
    //     verify(delegate, times(5)).maybeExecute(endpoint, request);
    //     futureResponse.set(mockResponse);
    //
    //     verify(delegate, times(7)).maybeExecute(endpoint, request);
    // }
    //
    // @Test
    // @SuppressWarnings("FutureReturnValueIgnored")
    // public void testQueueFullReturns429() throws ExecutionException, InterruptedException {
    //     queuedChannel = new QueuedChannel(delegate, 1, DispatcherMetrics.of(metrics));
    //
    //     mockNoCapacity();
    //     queuedChannel.execute(endpoint, request);
    //
    //     assertThat(queuedChannel.execute(endpoint, request).get().code()).isEqualTo(429);
    // }
    //
    // @Test
    // public void emitsQueueCapacityMetrics_whenChannelsHasNoCapacity() {
    //     mockNoCapacity();
    //
    //     queuedChannel.execute(endpoint, request);
    //     assertThat(gaugeValue(NUM_QUEUED_METRIC)).isEqualTo(1);
    //     assertThat(gaugeValue(NUM_RUNNING_METRICS)).isZero();
    //
    //     queuedChannel.execute(endpoint, request);
    //     assertThat(gaugeValue(NUM_QUEUED_METRIC)).isEqualTo(2);
    //     assertThat(gaugeValue(NUM_RUNNING_METRICS)).isZero();
    // }
    //
    // @Test
    // public void emitsQueueCapacityMetrics_whenChannelHasCapacity() {
    //     mockHasCapacity();
    //
    //     queuedChannel.execute(endpoint, request);
    //     assertThat(gaugeValue(NUM_QUEUED_METRIC)).isZero();
    //     assertThat(gaugeValue(NUM_RUNNING_METRICS)).isEqualTo(1);
    //
    //     futureResponse.set(mockResponse);
    //     assertThat(gaugeValue(NUM_QUEUED_METRIC)).isZero();
    //     assertThat(gaugeValue(NUM_RUNNING_METRICS)).isZero();
    // }
    //
    // @Test
    // @SuppressWarnings("FutureReturnValueIgnored")
    // public void testQueuedResponseClosedOnCancel() {
    //     Request queuedRequest =
    //             Request.builder().pathParams(ImmutableMap.of("foo", "bar")).build();
    //     when(delegate.maybeExecute(endpoint, queuedRequest)).thenReturn(Optional.empty());
    //     ListenableFuture<Response> result = queuedChannel.execute(endpoint, queuedRequest);
    //     verify(delegate, times(2)).maybeExecute(endpoint, queuedRequest);
    //
    //     when(delegate.maybeExecute(endpoint, request))
    //             .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
    //     when(delegate.maybeExecute(endpoint, queuedRequest))
    //             .thenAnswer((Answer<Optional<ListenableFuture<Response>>>) _invocation -> {
    //                 // cancel from this invocation to simulate the race between cancellation and execution
    //                 assertThat(result.cancel(true)).isTrue();
    //                 return Optional.of(Futures.immediateFuture(mockResponse));
    //             });
    //     // Force scheduling
    //     queuedChannel.execute(endpoint, request);
    //     assertThat(result).isCancelled();
    //     verify(delegate, times(1)).maybeExecute(endpoint, request);
    //     verify(mockResponse, times(1)).close();
    // }
    //
    // @Test
    // @SuppressWarnings("FutureReturnValueIgnored")
    // public void testQueuedResponsePropagatesCancel() {
    //     Request queued = Request.builder().putHeaderParams("key", "val").build();
    //     when(delegate.maybeExecute(endpoint, queued)).thenReturn(Optional.empty());
    //     ListenableFuture<Response> result = queuedChannel.execute(endpoint, queued);
    //     verify(delegate, times(2)).maybeExecute(endpoint, queued);
    //
    //     when(delegate.maybeExecute(endpoint, request))
    //             .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
    //     when(delegate.maybeExecute(endpoint, queued)).thenReturn(maybeResponse);
    //     queuedChannel.execute(endpoint, request);
    //     result.cancel(true);
    //     assertThat(futureResponse).isCancelled();
    //     verify(delegate, times(1)).maybeExecute(endpoint, request);
    //     verify(delegate, times(3)).maybeExecute(endpoint, queued);
    // }
    //
    // @Test
    // @SuppressWarnings("FutureReturnValueIgnored")
    // public void testQueuedResponseAvoidsExecutingCancelled() {
    //     Request queued = Request.builder().putHeaderParams("key", "val").build();
    //     when(delegate.maybeExecute(endpoint, queued)).thenReturn(Optional.empty());
    //     ListenableFuture<Response> result = queuedChannel.execute(endpoint, queued);
    //     verify(delegate, times(2)).maybeExecute(endpoint, queued);
    //
    //     assertThat(result.cancel(true)).isTrue();
    //     when(delegate.maybeExecute(endpoint, request))
    //             .thenReturn(Optional.of(Futures.immediateFuture(Mockito.mock(Response.class))));
    //     queuedChannel.execute(endpoint, request);
    //     verify(delegate, times(1)).maybeExecute(endpoint, request);
    //     // Should not have been invoked any more.
    //     verify(delegate, times(2)).maybeExecute(endpoint, queued);
    // }
    //
    // @SuppressWarnings("unchecked")
    // private Integer gaugeValue(MetricName metric) {
    //     return ((Gauge<Integer>) metrics.getMetrics().get(metric)).getValue();
    // }
    //
    // private OngoingStubbing<Optional<ListenableFuture<Response>>> mockHasCapacity() {
    //     return when(delegate.maybeExecute(endpoint, request)).thenReturn(maybeResponse);
    // }
    //
    // private OngoingStubbing<Optional<ListenableFuture<Response>>> mockNoCapacity() {
    //     return when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.empty());
    // }
}
