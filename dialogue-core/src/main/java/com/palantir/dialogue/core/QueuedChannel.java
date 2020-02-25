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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;

/**
 * A {@link Channel} that queues requests while the underlying {@link LimitedChannel} is unable to accept any new
 * requests. This is done by enqueueing requests on submission, and then running the schedule loop in one of 3 ways:
 * <ol>
 *     <li>On submission - allows execution when there is available capacity</li>
 *     <li>On request completion - allows execution when capacity has now become available</li>
 * </ol>
 *
 * This implementation was chosen over alternatives for the following reasons:
 * <ul>
 *     <li>Always periodically schedule: this decreases throughout as requests that may be able to run will have to
 *     wait until the next scheduling period</li>
 *     <li>Schedule in a spin loop: this would allow us to schedule without delay, but requires a thread constantly
 *     doing work, much of which will be wasted</li>
 * </ul>
 *
 * TODO(jellis): record metrics for queue sizes, num requests in flight, time spent in queue, etc.
 */
final class QueuedChannel implements LimitedChannel {
    @Override
    public ListenableFuture<LimitedResponse> maybeExecute(Endpoint endpoint, Request request) {
        return Futures.immediateFuture(LimitedResponses.clientLimited());
    }
    //
    // private static final Executor DIRECT = MoreExecutors.directExecutor();
    //
    // private final BlockingDeque<DeferredCall> queuedCalls;
    // private final LimitedChannel delegate;
    // // Tracks requests that are current executing in delegate and are not tracked in queuedCalls
    // private final AtomicInteger numRunningRequests = new AtomicInteger(0);
    //
    // QueuedChannel(LimitedChannel channel, DispatcherMetrics metrics) {
    //     this(channel, 1_000, metrics);
    // }
    //
    // @VisibleForTesting
    // @SuppressWarnings("FutureReturnValueIgnored")
    // QueuedChannel(LimitedChannel delegate, int maxQueueSize, DispatcherMetrics metrics) {
    //     this.delegate = new NeverThrowLimitedChannel(delegate);
    //     this.queuedCalls = new LinkedBlockingDeque<>(maxQueueSize);
    //
    //     metrics.callsQueued(queuedCalls::size);
    //     metrics.callsRunning(numRunningRequests::get);
    // }
    //
    // /**
    //  * Enqueues and tries to schedule as many queued tasks as possible.
    //  */
    // @Override
    // public ListenableFuture<LimitedResponse> maybeExecute(Endpoint endpoint, Request request) {
    //     // Optimistically avoid the queue in the fast path.
    //     // Queuing adds contention between threads and should be avoided unless we need to shed load.
    //     ListenableFuture<LimitedResponse> maybeResult = delegate.maybeExecute(endpoint, request);
    //     numRunningRequests.incrementAndGet();
    //     maybeResult.addListener(this::onCompletion, DIRECT);
    //     return Futures.transformAsync(
    //             maybeResult, response -> initialRequest(response, endpoint, request),
    // MoreExecutors.directExecutor());
    // }
    //
    // private ListenableFuture<LimitedResponse> initialRequest(
    //         LimitedResponse response, Endpoint endpoint, Request request) {
    //     if (response.matches(LimitedResponse.isClientLimited)) {
    //         DeferredCall components = DeferredCall.builder()
    //                 .endpoint(endpoint)
    //                 .request(request)
    //                 .response(SettableFuture.create())
    //                 .span(DetachedSpan.start("Dialogue-request-enqueued"))
    //                 .build();
    //
    //         if (!queuedCalls.offer(components)) {
    //             return Futures.immediateFuture(LimitedResponses.clientLimited());
    //         }
    //
    //         schedule();
    //
    //         return Futures.transformAsync(components.response(), response -> reschedule(response, endpoint,
    // request));
    //     }
    //     return Futures.immediateFuture(response);
    // }
    //
    // private void onCompletion() {
    //     numRunningRequests.decrementAndGet();
    //     schedule();
    // }
    //
    // /**
    //  * Try to schedule as many tasks as possible. Called when requests are submitted and when they complete.
    //  */
    // void schedule() {
    //     int numScheduled = 0;
    //     while (scheduleNextTask()) {
    //         numScheduled++;
    //     }
    //
    //     if (log.isDebugEnabled()) {
    //         log.debug("Scheduled {} requests", SafeArg.of("numScheduled", numScheduled));
    //     }
    // }
    //
    // /**
    //  * Get the next call and attempt to execute it. If it is runnable, wire up the underlying future to the one
    //  * previously returned to the caller. If it is not runnable, add it back into the queue. Returns true if more
    //  * tasks may be able to be scheduled, and false otherwise.
    //  */
    // private boolean scheduleNextTask() {
    //     DeferredCall components = queuedCalls.poll();
    //     if (components == null) {
    //         return false;
    //     }
    //     SettableFuture<LimitedResponse> queuedResponse = components.response();
    //     // If the future has been completed (most likely via cancel) the call should not be queued.
    //     // There's a race where cancel may be invoked between this check and execution, but the scheduled
    //     // request will be quickly cancelled in that case.
    //     if (queuedResponse.isDone()) {
    //         return true;
    //     }
    //     try (CloseableSpan ignored = components.span().childSpan("Dialogue-request-scheduled")) {
    //         Endpoint endpoint = components.endpoint();
    //         ListenableFuture<LimitedResponse> response = delegate.maybeExecute(endpoint, components.request());
    //
    //         components.span().complete();
    //         numRunningRequests.incrementAndGet();
    //         response.addListener(numRunningRequests::decrementAndGet, DIRECT);
    //         Futures.addCallback(response, new ForwardAndSchedule(queuedResponse), DIRECT);
    //         queuedResponse.addListener(
    //                 () -> {
    //                     if (queuedResponse.isCancelled()) {
    //                         // TODO(ckozak): Consider capturing the argument value provided to cancel to propagate
    //                         // here.
    //                         // Currently cancel(false) will be converted to cancel(true)
    //                         if (!response.cancel(true) && log.isDebugEnabled()) {
    //                             log.debug(
    //                                     "Failed to cancel delegate response, it should be reported by"
    //                                             + " ForwardAndSchedule logging",
    //                                     SafeArg.of("service", endpoint.serviceName()),
    //                                     SafeArg.of("endpoint", endpoint.endpointName()));
    //                         }
    //                     }
    //                 },
    //                 DIRECT);
    //         // TODO(forozco): Maybe theres a better way to handle removing from the queue
    //         return true;
    //     }
    // }
    //
    // /**
    //  * Forward the success or failure of the call to the SettableFuture that was previously returned to the caller.
    //  * This also schedules the next set of requests to be run.
    //  */
    // private class ForwardAndSchedule implements FutureCallback<LimitedResponse> {
    //     private final SettableFuture<LimitedResponse> response;
    //
    //     ForwardAndSchedule(SettableFuture<LimitedResponse> response) {
    //         this.response = response;
    //     }
    //
    //     @Override
    //     public void onSuccess(LimitedResponse result) {
    //         if (!response.set(result)) {
    //             LimitedResponses.getResponse(result).ifPresent(Response::close);
    //         }
    //         schedule();
    //     }
    //
    //     @Override
    //     public void onFailure(Throwable throwable) {
    //         if (!response.setException(throwable)) {
    //             log.info("Call failed after the future completed", throwable);
    //         }
    //         schedule();
    //     }
    // }
    //
    // @Value.Immutable
    // interface DeferredCall {
    //     Endpoint endpoint();
    //
    //     Request request();
    //
    //     SettableFuture<LimitedResponse> response();
    //
    //     DetachedSpan span();
    //
    //     class Builder extends ImmutableDeferredCall.Builder {}
    //
    //     static Builder builder() {
    //         return new Builder();
    //     }
    // }
}
