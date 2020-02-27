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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(QueuedChannel.class);
    private static final Executor DIRECT = MoreExecutors.directExecutor();

    private final Deque<DeferredCall> queuedCalls;
    private final LimitedChannel delegate;
    // Tracks requests that are current executing in delegate and are not tracked in queuedCalls
    private final AtomicInteger numRunningRequests = new AtomicInteger(0);
    private final AtomicInteger queueSizeEstimate = new AtomicInteger(0);
    private final int maxQueueSize;

    QueuedChannel(LimitedChannel channel) {
        this(channel, 1_000);
    }

    @VisibleForTesting
    QueuedChannel(LimitedChannel delegate, int maxQueueSize) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        // Do _not_ call size on a ConcurrentLinkedDeque. Unlike other collections, size is an O(n) operation.
        this.queuedCalls = new ProtectedConcurrentLinkedDeque<>();
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Enqueues and tries to schedule as many queued tasks as possible.
     */
    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(LimitedRequest request) {
        // Optimistically avoid the queue in the fast path.
        // Queuing adds contention between threads and should be avoided unless we need to shed load.
        if (queueSizeEstimate.get() <= 0) {
            Optional<ListenableFuture<Response>> maybeResult = delegate.maybeExecute(request);
            if (maybeResult.isPresent()) {
                ListenableFuture<Response> result = maybeResult.get();
                numRunningRequests.incrementAndGet();
                result.addListener(this::onCompletion, DIRECT);
                return maybeResult;
            }
        }

        // Important to read the queue size here as well as prior to the optimistic maybeExecute because
        // maybeExecute may take sufficiently long that other requests could be queued.
        if (queueSizeEstimate.get() >= maxQueueSize) {
            return Optional.empty();
        }

        DeferredCall components = DeferredCall.builder()
                .request(request)
                .response(SettableFuture.create())
                .span(DetachedSpan.start("Dialogue-request-enqueued"))
                .build();

        if (!queuedCalls.offer(components)) {
            // Should never happen, ConcurrentLinkedDeque has no maximum size
            return Optional.empty();
        }
        queueSizeEstimate.incrementAndGet();

        schedule();

        return Optional.of(components.response());
    }

    private void onCompletion() {
        numRunningRequests.decrementAndGet();
        schedule();
    }

    /**
     * Try to schedule as many tasks as possible. Called when requests are submitted and when they complete.
     */
    void schedule() {
        int numScheduled = 0;
        while (scheduleNextTask()) {
            numScheduled++;
        }

        if (log.isDebugEnabled()) {
            log.debug("Scheduled {} requests", SafeArg.of("numScheduled", numScheduled));
        }
    }

    /**
     * Get the next call and attempt to execute it. If it is runnable, wire up the underlying future to the one
     * previously returned to the caller. If it is not runnable, add it back into the queue. Returns true if more
     * tasks may be able to be scheduled, and false otherwise.
     */
    private boolean scheduleNextTask() {
        DeferredCall queueHead = queuedCalls.poll();
        if (queueHead == null) {
            return false;
        }
        SettableFuture<Response> queuedResponse = queueHead.response();
        // If the future has been completed (most likely via cancel) the call should not be queued.
        // There's a race where cancel may be invoked between this check and execution, but the scheduled
        // request will be quickly cancelled in that case.
        if (queuedResponse.isDone()) {
            queueSizeEstimate.decrementAndGet();
            return true;
        }
        try (CloseableSpan ignored = queueHead.span().childSpan("Dialogue-request-scheduled")) {
            LimitedRequest request = queueHead.request();
            Optional<ListenableFuture<Response>> maybeResponse = delegate.maybeExecute(request);

            if (maybeResponse.isPresent()) {
                queueSizeEstimate.decrementAndGet();
                ListenableFuture<Response> response = maybeResponse.get();
                queueHead.span().complete();
                numRunningRequests.incrementAndGet();
                response.addListener(numRunningRequests::decrementAndGet, DIRECT);
                Futures.addCallback(response, new ForwardAndSchedule(queuedResponse), DIRECT);
                queuedResponse.addListener(
                        () -> {
                            if (queuedResponse.isCancelled()) {
                                // TODO(ckozak): Consider capturing the argument value provided to cancel to propagate
                                // here.
                                // Currently cancel(false) will be converted to cancel(true)
                                if (!response.cancel(true) && log.isDebugEnabled()) {
                                    log.debug(
                                            "Failed to cancel delegate response, it should be reported by"
                                                    + " ForwardAndSchedule logging",
                                            SafeArg.of("request", request));
                                }
                            }
                        },
                        DIRECT);
                return true;
            } else {
                if (!queuedCalls.offerFirst(queueHead)) {
                    // Should never happen, ConcurrentLinkedDeque has no maximum size
                    log.error("Failed to add an attempted call back to the deque", SafeArg.of("request", request));
                    queueSizeEstimate.decrementAndGet();
                    if (!queuedResponse.setException(
                            new SafeRuntimeException("Failed to req-queue request", SafeArg.of("request", request)))) {
                        log.debug("Queued response has already been completed", SafeArg.of("request", request));
                    }
                }
                return false;
            }
        }
    }

    /**
     * Forward the success or failure of the call to the SettableFuture that was previously returned to the caller.
     * This also schedules the next set of requests to be run.
     */
    private class ForwardAndSchedule implements FutureCallback<Response> {
        private final SettableFuture<Response> response;

        ForwardAndSchedule(SettableFuture<Response> response) {
            this.response = response;
        }

        @Override
        public void onSuccess(Response result) {
            if (!response.set(result)) {
                result.close();
            }
            schedule();
        }

        @Override
        public void onFailure(Throwable throwable) {
            if (!response.setException(throwable)) {
                log.info("Call failed after the future completed", throwable);
            }
            schedule();
        }
    }

    @Value.Immutable
    interface DeferredCall {
        LimitedRequest request();

        SettableFuture<Response> response();

        DetachedSpan span();

        class Builder extends ImmutableDeferredCall.Builder {}

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
