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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class QueuedChannel2 implements BlockingChannel {
    private static final Logger log = LoggerFactory.getLogger(QueuedChannel2.class);

    private final Deque<CountDownLatch> queuedCalls;
    private final LimitedBlockingChannel delegate;
    // private final String channelName;
    // // Tracks requests that are current executing in delegate and are not tracked in queuedCalls
    private final AtomicInteger queueSizeEstimate = new AtomicInteger(0);
    // private final int maxQueueSize;
    // private final Counter queueSizeCounter;
    // private final Timer queuedTime;
    private final Supplier<RuntimeException> limitedResultSupplier;

    QueuedChannel2(
            LimitedBlockingChannel delegate, String channelName, TaggedMetricRegistry metrics, int maxQueueSize) {
        this.delegate = delegate;
        // this.channelName = channelName;
        // // Do _not_ call size on a ConcurrentLinkedDeque. Unlike other collections, size is an O(n) operation.
        this.queuedCalls = new ProtectedConcurrentLinkedDeque<CountDownLatch>();
        // this.maxQueueSize = maxQueueSize;
        // this.queueSizeCounter = DialogueClientMetrics.of(metrics).requestsQueued(channelName);
        // this.queuedTime = DialogueClientMetrics.of(metrics).requestQueuedTime(channelName);
        this.limitedResultSupplier = () -> new SafeRuntimeException(
                "Unable to make a request (queue is full)", SafeArg.of("maxQueueSize", maxQueueSize));
    }

    // static QueuedChannel2 create(Config cf, LimitedChannel delegate) {
    //     return new QueuedChannel2(
    //             delegate, cf.channelName(), cf.clientConf().taggedMetricRegistry(), cf.maxQueueSize());
    // }

    @Override
    public Response execute(Endpoint endpoint, Request request) throws IOException {
        return maybeExecute(endpoint, request).orElseThrow(limitedResultSupplier);
    }

    /**
     * Enqueues and tries to schedule as many queued tasks as possible.
     */
    @VisibleForTesting
    Optional<Response> maybeExecute(Endpoint endpoint, Request request) throws IOException {
        if (queueSizeEstimate.get() == 0) {
            Optional<Response> maybeResponse = delegate.maybeExecute(endpoint, request);
            if (maybeResponse.isPresent()) {
                return maybeResponse; // fast path, no contention :)
            } else {

                queuedCalls.push(new CountDownLatch(1));
            }
        }
    }
    //
    // private void onCompletion() {
    //     schedule();
    // }
    //
    // /**
    //  * Try to schedule as many tasks as possible. Called when requests are submitted and when they complete.
    //  */
    // @VisibleForTesting
    // void schedule() {
    //     int numScheduled = 0;
    //     while (scheduleNextTask()) {
    //         numScheduled++;
    //     }
    //
    //     if (log.isDebugEnabled()) {
    //         log.debug(
    //                 "Scheduled {} requests on channel {}",
    //                 SafeArg.of("numScheduled", numScheduled),
    //                 SafeArg.of("channelName", channelName));
    //     }
    // }
    //
    // private int incrementQueueSize() {
    //     queueSizeCounter.inc();
    //     return queueSizeEstimate.incrementAndGet();
    // }
    //
    // private void decrementQueueSize() {
    //     queueSizeEstimate.decrementAndGet();
    //     queueSizeCounter.dec();
    // }
    //
    // /**
    //  * Get the next call and attempt to execute it. If it is runnable, wire up the underlying future to the one
    //  * previously returned to the caller. If it is not runnable, add it back into the queue. Returns true if more
    //  * tasks may be able to be scheduled, and false otherwise.
    //  */
    // private boolean scheduleNextTask() {
    //     DeferredCall queueHead = queuedCalls.poll();
    //     if (queueHead == null) {
    //         return false;
    //     }
    //     SettableFuture<Response> queuedResponse = queueHead.response();
    //     // If the future has been completed (most likely via cancel) the call should not be queued.
    //     // There's a race where cancel may be invoked between this check and execution, but the scheduled
    //     // request will be quickly cancelled in that case.
    //     if (queuedResponse.isDone()) {
    //         decrementQueueSize();
    //         queueHead.span().complete();
    //         queueHead.timer().stop();
    //         return true;
    //     }
    //     try (CloseableSpan ignored = queueHead.span().childSpan("Dialogue-request-scheduled")) {
    //         Endpoint endpoint = queueHead.endpoint();
    //         Optional<ListenableFuture<Response>> maybeResponse = delegate.maybeExecute(endpoint,
    // queueHead.request());
    //
    //         if (maybeResponse.isPresent()) {
    //             decrementQueueSize();
    //             ListenableFuture<Response> response = maybeResponse.get();
    //             queueHead.span().complete();
    //             queueHead.timer().stop();
    //             DialogueFutures.addDirectCallback(response, new ForwardAndSchedule(queuedResponse));
    //             DialogueFutures.addDirectListener(queuedResponse, () -> {
    //                 if (queuedResponse.isCancelled()) {
    //                     // TODO(ckozak): Consider capturing the argument value provided to cancel to propagate
    //                     // here.
    //                     // Currently cancel(false) will be converted to cancel(true)
    //                     if (!response.cancel(true) && log.isDebugEnabled()) {
    //                         log.debug(
    //                                 "Failed to cancel delegate response, it should be reported by ForwardAndSchedule
    // "
    //                                         + "logging",
    //                                 SafeArg.of("channel", channelName),
    //                                 SafeArg.of("service", endpoint.serviceName()),
    //                                 SafeArg.of("endpoint", endpoint.endpointName()));
    //                     }
    //                 }
    //             });
    //             return true;
    //         } else {
    //             if (!queuedCalls.offerFirst(queueHead)) {
    //                 // Should never happen, ConcurrentLinkedDeque has no maximum size
    //                 log.error(
    //                         "Failed to add an attempted call back to the deque",
    //                         SafeArg.of("channel", channelName),
    //                         SafeArg.of("service", endpoint.serviceName()),
    //                         SafeArg.of("endpoint", endpoint.endpointName()));
    //                 decrementQueueSize();
    //                 queueHead.timer().stop();
    //                 if (!queuedResponse.setException(new SafeRuntimeException(
    //                         "Failed to req-queue request",
    //                         SafeArg.of("channel", channelName),
    //                         SafeArg.of("service", endpoint.serviceName()),
    //                         SafeArg.of("endpoint", endpoint.endpointName())))) {
    //                     log.debug(
    //                             "Queued response has already been completed",
    //                             SafeArg.of("channel", channelName),
    //                             SafeArg.of("service", endpoint.serviceName()),
    //                             SafeArg.of("endpoint", endpoint.endpointName()));
    //                 }
    //             }
    //             return false;
    //         }
    //     }
    // }
    //
    // @Override
    // public String toString() {
    //     return "QueuedChannel{queueSizeEstimate="
    //             + queueSizeEstimate + ", maxQueueSize="
    //             + maxQueueSize + ", delegate="
    //             + delegate + '}';
    // }

    // /**
    //  * Forward the success or failure of the call to the SettableFuture that was previously returned to the caller.
    //  * This also schedules the next set of requests to be run.
    //  */
    // private class ForwardAndSchedule implements FutureCallback<Response> {
    //     private final SettableFuture<Response> response;
    //
    //     ForwardAndSchedule(SettableFuture<Response> response) {
    //         this.response = response;
    //     }
    //
    //     @Override
    //     public void onSuccess(Response result) {
    //         if (!response.set(result)) {
    //             result.close();
    //         }
    //         schedule();
    //     }
    //
    //     @Override
    //     public void onFailure(Throwable throwable) {
    //         if (!response.setException(throwable)) {
    //             if (throwable instanceof CancellationException) {
    //                 log.debug("Call was canceled", throwable);
    //             } else {
    //                 log.info("Call failed after the future completed", throwable);
    //             }
    //         }
    //         schedule();
    //     }
    // }

    @Value.Immutable
    interface DeferredCall2 {
        // Endpoint endpoint();

        // Request request();

        // SettableFuture<Response> response();

        // DetachedSpan span();
        //
        // Timer.Context timer();

        class Builder extends ImmutableDeferredCall2.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }

    private static final class ProtectedConcurrentLinkedDeque<T> extends ConcurrentLinkedDeque<T> {

        @Override
        public int size() {
            throw new UnsupportedOperationException("size should never be called on a ConcurrentLinkedDeque");
        }
    }
}
