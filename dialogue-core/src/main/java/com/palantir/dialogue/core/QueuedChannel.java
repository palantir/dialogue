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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.TagTranslator;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.immutables.value.Value;

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
 */
final class QueuedChannel implements Channel {
    private static final SafeLogger log = SafeLoggerFactory.get(QueuedChannel.class);
    private static final LimitEnforcement DO_NOT_SKIP_LIMITS = LimitEnforcement.DEFAULT_ENABLED;

    private final Deque<DeferredCall> queuedCalls;
    private final NeverThrowLimitedChannel delegate;

    @Safe
    private final String channelName;

    @Safe
    private final String queueType;
    // Tracks requests that are current executing in delegate and are not tracked in queuedCalls
    private final AtomicInteger queueSizeEstimate = new AtomicInteger(0);
    private final int maxQueueSize;
    private final Supplier<Counter> queueSizeCounter;
    private final Timer queuedTime;
    private final Supplier<ListenableFuture<Response>> limitedResultSupplier;
    // Metrics aren't reported until the queue is first used, allowing per-endpoint queues to
    // avoid creating unnecessary data.
    private volatile boolean shouldRecordQueueMetrics;

    QueuedChannel(
            LimitedChannel delegate,
            @Safe String channelName,
            @Safe String queueType,
            QueuedChannelInstrumentation metrics,
            int maxQueueSize) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.channelName = channelName;
        this.queueType = queueType;
        // Do _not_ call size on a ConcurrentLinkedDeque. Unlike other collections, size is an O(n) operation.
        this.queuedCalls = new ProtectedConcurrentLinkedDeque<>();
        this.maxQueueSize = maxQueueSize;
        // Lazily create the counter. Unlike meters, timers, and histograms, counters cannot be ignored when they have
        // zero interactions because they support both increment and decrement operations.
        this.queueSizeCounter = Suppliers.memoize(metrics::requestsQueued);
        this.queuedTime = metrics.requestQueuedTime();
        this.limitedResultSupplier = () -> Futures.immediateFailedFuture(new SafeRuntimeException(
                "Unable to make a request (queue is full)", SafeArg.of("maxQueueSize", maxQueueSize)));
    }

    // Metrics are global, even if max size is per queue.
    static QueuedChannel createForSticky(
            String channelName,
            int maxQueueSize,
            QueuedChannelInstrumentation queuedChannelInstrumentation,
            LimitedChannel delegate) {
        return new QueuedChannel(delegate, channelName, "sticky", queuedChannelInstrumentation, maxQueueSize);
    }

    static QueuedChannel create(Config cf, LimitedChannel delegate) {
        return new QueuedChannel(
                delegate,
                cf.channelName(),
                "channel",
                channelInstrumentation(
                        DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry()), cf.channelName()),
                cf.maxQueueSize());
    }

    static QueuedChannel create(Config cf, Endpoint endpoint, LimitedChannel delegate) {
        return new QueuedChannel(
                delegate,
                cf.channelName(),
                "endpoint",
                endpointInstrumentation(
                        DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry()),
                        cf.channelName(),
                        endpoint.serviceName(),
                        endpoint.endpointName()),
                cf.maxQueueSize());
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return maybeExecute(endpoint, request).orElseGet(limitedResultSupplier);
    }

    /**
     * Enqueues and tries to schedule as many queued tasks as possible.
     */
    @VisibleForTesting
    @SuppressWarnings("PreferJavaTimeOverload")
    Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        // Optimistically avoid the queue in the fast path.
        // Queuing adds contention between threads and should be avoided unless we need to shed load.
        if (queueSizeEstimate.get() <= 0) {
            Optional<ListenableFuture<Response>> maybeResult =
                    delegate.maybeExecute(endpoint, request, DO_NOT_SKIP_LIMITS);
            if (maybeResult.isPresent()) {
                ListenableFuture<Response> result = maybeResult.get();
                DialogueFutures.addDirectListener(result, this::onCompletion);
                // While the queue was avoid, this is equivalent to spending zero time on the queue.
                if (shouldRecordQueueMetrics) {
                    queuedTime.update(0, TimeUnit.NANOSECONDS);
                }
                return maybeResult;
            }
        }

        // Important to read the queue size here as well as prior to the optimistic maybeExecute because
        // maybeExecute may take sufficiently long that other requests could be queued.
        if (queueSizeEstimate.get() >= maxQueueSize) {
            return Optional.empty();
        }

        shouldRecordQueueMetrics = true;

        DeferredCall components = DeferredCall.builder()
                .endpoint(endpoint)
                .request(request)
                .response(SettableFuture.create())
                .span(DetachedSpan.start("Dialogue-request-enqueued"))
                .timer(queuedTime.time())
                .build();

        if (!queuedCalls.offer(components)) {
            // Should never happen, ConcurrentLinkedDeque has no maximum size
            return Optional.empty();
        }
        int newSize = incrementQueueSize();

        if (log.isDebugEnabled()) {
            log.debug(
                    "Request queued {} on channel {}",
                    SafeArg.of("queueSize", newSize),
                    SafeArg.of("channelName", channelName));
        }

        schedule();

        return Optional.of(components.response());
    }

    private void onCompletion() {
        schedule();
    }

    /**
     * Try to schedule as many tasks as possible. Called when requests are submitted and when they complete.
     */
    @VisibleForTesting
    void schedule() {
        int numScheduled = 0;
        while (scheduleNextTask()) {
            numScheduled++;
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "Scheduled {} requests on channel {}",
                    SafeArg.of("numScheduled", numScheduled),
                    SafeArg.of("channelName", channelName));
        }
    }

    private int incrementQueueSize() {
        queueSizeCounter.get().inc();
        return queueSizeEstimate.incrementAndGet();
    }

    private void decrementQueueSize() {
        queueSizeEstimate.decrementAndGet();
        queueSizeCounter.get().dec();
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
            decrementQueueSize();
            queueHead.span().complete(QueuedChannelTagTranslator.INSTANCE, this);
            queueHead.timer().stop();
            return true;
        }
        try (CloseableSpan ignored = queueHead.span().attach()) {
            Endpoint endpoint = queueHead.endpoint();
            Optional<ListenableFuture<Response>> maybeResponse =
                    delegate.maybeExecute(endpoint, queueHead.request(), DO_NOT_SKIP_LIMITS);

            if (maybeResponse.isPresent()) {
                decrementQueueSize();
                ListenableFuture<Response> response = maybeResponse.get();
                queueHead.span().complete(QueuedChannelTagTranslator.INSTANCE, this);
                queueHead.timer().stop();
                DialogueFutures.addDirectCallback(response, new ForwardAndSchedule(queuedResponse));
                DialogueFutures.addDirectListener(queuedResponse, () -> {
                    if (queuedResponse.isCancelled()) {
                        // TODO(ckozak): Consider capturing the argument value provided to cancel to propagate
                        // here.
                        // Currently cancel(false) will be converted to cancel(true)
                        if (!response.cancel(true) && log.isDebugEnabled()) {
                            log.debug(
                                    "Failed to cancel delegate response, it should be reported by ForwardAndSchedule "
                                            + "logging",
                                    SafeArg.of("channel", channelName),
                                    SafeArg.of("service", endpoint.serviceName()),
                                    SafeArg.of("endpoint", endpoint.endpointName()));
                        }
                    }
                });
                return true;
            } else {
                if (!queuedCalls.offerFirst(queueHead)) {
                    // Should never happen, ConcurrentLinkedDeque has no maximum size
                    log.error(
                            "Failed to add an attempted call back to the deque",
                            SafeArg.of("channel", channelName),
                            SafeArg.of("service", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName()));
                    decrementQueueSize();
                    queueHead.timer().stop();
                    if (!queuedResponse.setException(new SafeRuntimeException(
                            "Failed to req-queue request",
                            SafeArg.of("channel", channelName),
                            SafeArg.of("service", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName())))) {
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "Queued response has already been completed",
                                    SafeArg.of("channel", channelName),
                                    SafeArg.of("service", endpoint.serviceName()),
                                    SafeArg.of("endpoint", endpoint.endpointName()));
                        }
                    }
                }
                return false;
            }
        }
    }

    @Override
    public String toString() {
        return "QueuedChannel{queueSizeEstimate="
                + queueSizeEstimate + ", maxQueueSize="
                + maxQueueSize + ", delegate="
                + delegate + '}';
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
                if (throwable instanceof CancellationException) {
                    log.debug("Call was canceled", throwable);
                } else {
                    log.info("Call failed after the future completed", throwable);
                }
            }
            schedule();
        }
    }

    @Value.Immutable
    interface DeferredCall {
        Endpoint endpoint();

        Request request();

        SettableFuture<Response> response();

        DetachedSpan span();

        Timer.Context timer();

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

    interface QueuedChannelInstrumentation {
        Counter requestsQueued();

        Timer requestQueuedTime();
    }

    static QueuedChannelInstrumentation channelInstrumentation(DialogueClientMetrics metrics, String channelName) {
        return new QueuedChannelInstrumentation() {
            @Override
            public Counter requestsQueued() {
                return metrics.requestsQueued(channelName);
            }

            @Override
            public Timer requestQueuedTime() {
                return metrics.requestQueuedTime(channelName);
            }
        };
    }

    static QueuedChannelInstrumentation stickyInstrumentation(DialogueClientMetrics metrics, String channelName) {
        // Sticky-session queue instrumentation is reused between sticky sessions, metric references are
        // memoized in order to avoid unnecessary churn.
        return new MemoizedQueuedChannelInstrumentation(new QueuedChannelInstrumentation() {
            @Override
            public Counter requestsQueued() {
                return metrics.requestsStickyQueued(channelName);
            }

            @Override
            public Timer requestQueuedTime() {
                return metrics.requestStickyQueuedTime(channelName);
            }
        });
    }

    static QueuedChannelInstrumentation endpointInstrumentation(
            DialogueClientMetrics metrics, String channelName, String service, String endpoint) {
        return new QueuedChannelInstrumentation() {
            @Override
            public Counter requestsQueued() {
                return metrics.requestsEndpointQueued()
                        .channelName(channelName)
                        .serviceName(service)
                        .endpoint(endpoint)
                        .build();
            }

            @Override
            public Timer requestQueuedTime() {
                return metrics.requestEndpointQueuedTime()
                        .channelName(channelName)
                        .serviceName(service)
                        .endpoint(endpoint)
                        .build();
            }
        };
    }

    private static final class MemoizedQueuedChannelInstrumentation implements QueuedChannelInstrumentation {

        private final Supplier<Counter> requestsQueuedSupplier;
        private final Supplier<Timer> requestQueuedTimeSupplier;

        MemoizedQueuedChannelInstrumentation(QueuedChannelInstrumentation delegate) {
            this.requestsQueuedSupplier = Suppliers.memoize(delegate::requestsQueued);
            this.requestQueuedTimeSupplier = Suppliers.memoize(delegate::requestQueuedTime);
        }

        @Override
        public Counter requestsQueued() {
            return requestsQueuedSupplier.get();
        }

        @Override
        public Timer requestQueuedTime() {
            return requestQueuedTimeSupplier.get();
        }
    }

    private enum QueuedChannelTagTranslator implements TagTranslator<QueuedChannel> {
        INSTANCE;

        @Override
        public <T> void translate(TagAdapter<T> adapter, T target, QueuedChannel data) {
            adapter.tag(target, "queue", data.queueType);
            adapter.tag(target, "channel", data.channelName);
        }
    }
}
