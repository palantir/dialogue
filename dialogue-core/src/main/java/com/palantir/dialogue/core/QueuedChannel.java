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
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.TagTranslator;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

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
    private static final String TIMEOUT_SCHEDULER_NAME = "dialogue-QueuedChannel-timeout-scheduler";

    @SuppressWarnings("deprecation") // Singleton registry for a singleton executor
    static final Supplier<ScheduledExecutorService> sharedTimeoutScheduler =
            Suppliers.memoize(() -> DialogueExecutors.newSharedSingleThreadScheduler(MetricRegistries.instrument(
                    SharedTaggedMetricRegistries.getSingleton(),
                    new ThreadFactoryBuilder()
                            .setNameFormat(TIMEOUT_SCHEDULER_NAME + "-%d")
                            .setDaemon(true)
                            .build(),
                    TIMEOUT_SCHEDULER_NAME)));

    @Nullable
    static ScheduledExecutorService timeoutScheduler(OptionalLong queueTimeoutNanos) {
        return queueTimeoutNanos.isPresent() ? sharedTimeoutScheduler.get() : null;
    }

    private final Deque<DeferredCall> queuedCalls;
    private final NeverThrowLimitedChannel delegate;

    @Safe
    private final String channelName;

    @Safe
    private final String queueType;

    // Inexpensive tracker for queuedCalls.size(), due to the high cost of
    // ConcurrentLinkedDeque.size(). Our ProtectedConcurrentLinkedDeque subtype
    // makes size() throw.
    private final AtomicInteger queueSizeEstimate = new AtomicInteger(0);
    private final int maxQueueSize;
    private final Supplier<Counter> queueSizeCounter;
    private final Supplier<Counter> queueTimeoutCounter;
    private final Timer queuedTime;
    private final Supplier<ListenableFuture<Response>> limitedResultSupplier;
    // Metrics aren't reported until the queue is first used, allowing per-endpoint queues to
    // avoid creating unnecessary data.
    private volatile boolean shouldRecordQueueMetrics;

    // Tracks requests that are current executing in delegate and are not tracked in queuedCalls
    private final AtomicInteger inFlight = new AtomicInteger();

    // The timeout budget is shared across both queue layers via a Request attachment (see QueueTimeoutAttachments).
    @Safe
    private final OptionalLong queueTimeoutNanos;

    private final Ticker clock;

    @Nullable
    private final ScheduledExecutorService scheduler;

    QueuedChannel(
            LimitedChannel delegate,
            @Safe String channelName,
            @Safe String queueType,
            QueuedChannelInstrumentation metrics,
            int maxQueueSize,
            OptionalLong queueTimeoutNanos,
            Ticker clock,
            @Nullable ScheduledExecutorService scheduler) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.channelName = channelName;
        this.queueType = queueType;
        // Do _not_ call size on a ConcurrentLinkedDeque. Unlike other collections, size is an O(n) operation.
        this.queuedCalls = new ProtectedConcurrentLinkedDeque<>();
        this.maxQueueSize = maxQueueSize;
        this.queueTimeoutNanos = queueTimeoutNanos;
        this.clock = clock;
        this.scheduler = scheduler;
        // Lazily create the counter. Unlike meters, timers, and histograms, counters cannot be ignored when they have
        // zero interactions because they support both increment and decrement operations.
        this.queueSizeCounter = Suppliers.memoize(metrics::requestsQueued);
        this.queueTimeoutCounter = Suppliers.memoize(metrics::requestQueueTimeout);
        this.queuedTime = metrics.requestQueuedTime();
        this.limitedResultSupplier = () -> {
            List<SafeArg<?>> safeArgs = new ArrayList<>(metrics.queueFullSafeArgs());
            safeArgs.add(SafeArg.of("maxQueueSize", maxQueueSize));
            safeArgs.add(SafeArg.of("channelName", channelName));
            return Futures.immediateFailedFuture(new SafeRuntimeException(
                    "Unable to make a request (queue is full)", safeArgs.toArray(SafeArg[]::new)));
        };
    }

    // Metrics are global, even if max size is per queue.
    static QueuedChannel createForSticky(
            String channelName,
            int maxQueueSize,
            QueuedChannelInstrumentation queuedChannelInstrumentation,
            LimitedChannel delegate,
            OptionalLong queueTimeoutNanos,
            Ticker clock,
            @Nullable ScheduledExecutorService scheduler) {
        return new QueuedChannel(
                delegate,
                channelName,
                "sticky",
                queuedChannelInstrumentation,
                maxQueueSize,
                queueTimeoutNanos,
                clock,
                scheduler);
    }

    static QueuedChannel create(Config cf, LimitedChannel delegate) {
        OptionalLong timeoutNanos =
                cf.queueTimeout().map(Duration::toNanos).map(OptionalLong::of).orElseGet(OptionalLong::empty);
        return new QueuedChannel(
                delegate,
                cf.channelName(),
                "channel",
                channelInstrumentation(
                        DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry()), cf.channelName()),
                cf.maxQueueSize(),
                timeoutNanos,
                cf.ticker(),
                timeoutScheduler(timeoutNanos));
    }

    static QueuedChannel create(Config cf, Endpoint endpoint, LimitedChannel delegate) {
        OptionalLong timeoutNanos =
                cf.queueTimeout().map(Duration::toNanos).map(OptionalLong::of).orElseGet(OptionalLong::empty);
        return new QueuedChannel(
                delegate,
                cf.channelName(),
                "endpoint",
                endpointInstrumentation(
                        DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry()),
                        cf.channelName(),
                        endpoint.serviceName(),
                        endpoint.endpointName()),
                cf.maxQueueSize(),
                timeoutNanos,
                cf.ticker(),
                timeoutScheduler(timeoutNanos));
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
            LimitEnforcement limitEnforcement = limitEnforcement();
            Optional<ListenableFuture<Response>> maybeResult =
                    delegate.maybeExecute(endpoint, request, limitEnforcement);
            if (maybeResult.isPresent()) {
                inFlight.incrementAndGet();
                ListenableFuture<Response> result = maybeResult.get();
                DialogueFutures.addDirectListener(result, this::onCompletion);
                // While the queue was avoid, this is equivalent to spending zero time on the queue.
                if (shouldRecordQueueMetrics) {
                    queuedTime.update(0, TimeUnit.NANOSECONDS);
                }
                return maybeResult;
            } else if (!limitEnforcement.enforceLimits()) {
                return Optional.of(Futures.immediateFailedFuture(limitEnforcementExpectationFailure(endpoint)));
            }
        }

        // Important to read the queue size here as well as prior to the optimistic maybeExecute because
        // maybeExecute may take sufficiently long that other requests could be queued.
        if (queueSizeEstimate.get() >= maxQueueSize) {
            return Optional.empty();
        }

        shouldRecordQueueMetrics = true;

        SettableFuture<Response> responseFuture = SettableFuture.create();
        DetachedSpan span = DetachedSpan.start("Dialogue-request-enqueued");
        IdempotentTimerContext timer = new IdempotentTimerContext(queuedTime.time());
        QueueSizeAccounting accounting = new QueueSizeAccounting();
        Optional<ScheduledFuture<?>> timeoutFuture =
                Optional.ofNullable(scheduleQueueTimeout(request, responseFuture, span, timer, accounting));

        // If the timeout budget was already exhausted (e.g., from time spent in a previous queue),
        // scheduleQueueTimeout failed the future immediately. Return early to avoid creating a
        // DeferredCall and adding it to the deque only for scheduleNextTask to clean it up.
        if (responseFuture.isDone()) {
            return Optional.of(responseFuture);
        }

        // The queue-timeout expiration is stamped on a Request attachment and shared across queue layers for the
        // duration of a single execution attempt. The queue-timeout budget is reset on every retry, not carried over.
        // Clearing it when this attempt terminates is what gives a retried request a fresh queue-timeout budget:
        // RetryingChannel sits above the queues and re-executes the same Request instance, so once this attempt's
        // response future completes the attachment is cleared and the retry's next enqueue stamps a brand-new
        // expiration rather than inheriting this attempt's remaining budget.
        if (queueTimeoutNanos.isPresent()) {
            DialogueFutures.addDirectListener(responseFuture, () -> QueueTimeoutAttachments.clearExpiration(request));
        }

        DeferredCall components = DeferredCall.builder()
                .endpoint(endpoint)
                .request(request)
                .response(responseFuture)
                .span(span)
                .timer(timer)
                .timeoutFuture(timeoutFuture)
                .accounting(accounting)
                .build();

        if (!queuedCalls.offer(components)) {
            // Should never happen, ConcurrentLinkedDeque has no maximum size
            return Optional.empty();
        }
        int newSize = accounting.incrementAndGet();

        if (log.isDebugEnabled()) {
            log.debug(
                    "Request queued {} on channel {}",
                    SafeArg.of("queueSize", newSize),
                    SafeArg.of("channelName", channelName));
        }

        schedule();

        return Optional.of(components.response());
    }

    @Nullable
    private ScheduledFuture<?> scheduleQueueTimeout(
            Request request,
            SettableFuture<Response> responseFuture,
            DetachedSpan span,
            IdempotentTimerContext timer,
            QueueSizeAccounting accounting) {
        if (queueTimeoutNanos.isEmpty()) {
            return null;
        }
        Preconditions.checkNotNull(scheduler, "Scheduler must be present when queue timeouts are enabled");
        QueueTimeoutAttachments.setExpirationIfAbsent(request, clock.read() + queueTimeoutNanos.getAsLong());
        return scheduleTimeoutFromExpiration(request, responseFuture, span, timer, accounting);
    }

    /**
     * Schedules a timeout task based on the expiration already stamped on the request attachment.
     */
    @Nullable
    private ScheduledFuture<?> scheduleTimeoutFromExpiration(
            Request request,
            SettableFuture<Response> responseFuture,
            DetachedSpan span,
            IdempotentTimerContext timer,
            QueueSizeAccounting accounting) {
        if (queueTimeoutNanos.isEmpty() || scheduler == null) {
            return null;
        }
        Long expirationNanos = QueueTimeoutAttachments.getExpiration(request);
        if (expirationNanos == null) {
            return null;
        }
        long delayNanos = expirationNanos - clock.read();
        if (delayNanos <= 0) {
            // The timeout is already reached, so we fail immediately. The queue-size decrement is safe to request
            // unconditionally: on initial enqueue the entry has not been counted yet (maybeExecute returns early,
            // before incrementing), so it is a no-op; on re-queue the entry is counted and gets decremented here.
            failWithQueueTimeout(responseFuture, span, timer, accounting);
            return null;
        }
        // When the timeout fires, failWithQueueTimeout proactively decrements the queue size, so the queue size
        // reflects only live requests rather than lingering until the next scheduleNextTask() drain pops the entry.
        return scheduler.schedule(
                () -> failWithQueueTimeout(responseFuture, span, timer, accounting), delayNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Fails the given future with a queue timeout exception and performs the same terminal cleanup as a normal
     * dequeue: it decrements the queue size (at most once, and only if the entry was counted) and eagerly completes
     * the span and stops the timer. Doing this eagerly avoids waiting for the next {@link #scheduleNextTask()} drain
     * cycle, which could be much later in some scenarios (e.g. all hosts are stuck).
     * <p>
     * There is no pending timeout task to cancel here, because this runs <em>as</em> the timeout (or as an
     * already-expired immediate failure). {@link #completeAndDecrement} and its constituent operations are all
     * idempotent, so the subsequent cleanup in {@link #scheduleNextTask()}'s {@code isDone()} check is harmless.
     */
    @VisibleForTesting
    void failWithQueueTimeout(
            SettableFuture<Response> responseFuture,
            DetachedSpan span,
            IdempotentTimerContext timer,
            QueueSizeAccounting accounting) {
        if (responseFuture.setException(new QueueTimeoutException(channelName, queueTimeoutNanos))) {
            queueTimeoutCounter.get().inc();
        }
        completeAndDecrement(Optional.empty(), span, timer, accounting);
    }

    private void onCompletion() {
        // decrementing inflight must occur prior to calling schedule, ensuring that
        // schedule may be called after inflight is returned to zero.
        inFlight.decrementAndGet();
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

    final class QueueSizeAccounting {
        private final AtomicBoolean incremented = new AtomicBoolean(false);
        private final AtomicBoolean decremented = new AtomicBoolean(false);

        int incrementAndGet() {
            int newSize = incrementQueueSize();
            incremented.set(true);
            return newSize;
        }

        /** Decrements the queue size iff it was incremented and has not already been decremented. */
        void decrementIfCounted() {
            if (incremented.get() && decremented.compareAndSet(false, true)) {
                decrementQueueSize();
            }
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

        // Cancel the timeout proactively before dispatch
        queueHead.timeoutFuture().ifPresent(future -> future.cancel(false));

        // If the future has been completed (via cancel, queue timeout that won the race
        // before we cancelled, or any other reason), clean up without dispatching.
        if (queueHead.response().isDone()) {
            cleanupDeferredCall(queueHead);
            return true;
        }
        return scheduleTaskFromQueue(queueHead);
    }

    private boolean scheduleTaskFromQueue(DeferredCall queueHead) {
        SettableFuture<Response> queuedResponse = queueHead.response();
        try (CloseableSpan ignored = queueHead.span().attach()) {
            Endpoint endpoint = queueHead.endpoint();
            LimitEnforcement limitEnforcement = limitEnforcement();
            Optional<ListenableFuture<Response>> maybeResponse =
                    delegate.maybeExecute(endpoint, queueHead.request(), limitEnforcement);

            if (maybeResponse.isPresent()) {
                cleanupDeferredCall(queueHead);
                inFlight.incrementAndGet();
                ListenableFuture<Response> response = maybeResponse.get();
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
            } else if (!limitEnforcement.enforceLimits()) {
                cleanupDeferredCall(queueHead);
                queuedResponse.setException(limitEnforcementExpectationFailure(queueHead.endpoint()));
                log.warn(
                        "Failed to make a request bypassing concurrency limits, which should not be possible",
                        SafeArg.of("channel", channelName),
                        SafeArg.of("service", endpoint.serviceName()),
                        SafeArg.of("endpoint", endpoint.endpointName()));
                return true;
            } else {
                // Delegate rejected. We need to re-queue with a timeout for the remaining budget.
                DeferredCall requeued = addTimeoutOnRequeue(queueHead);
                if (!queuedCalls.offerFirst(requeued)) {
                    // Should never happen, ConcurrentLinkedDeque has no maximum size
                    log.error(
                            "Failed to add an attempted call back to the deque",
                            SafeArg.of("channel", channelName),
                            SafeArg.of("service", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName()));
                    cleanupDeferredCall(requeued);
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

    /**
     * Cleans up a {@link DeferredCall} that is leaving the deque. It cancels the pending timeout task, decrements the
     * queue size, completes the span, and stops the timer.
     */
    private void cleanupDeferredCall(DeferredCall call) {
        completeAndDecrement(call.timeoutFuture(), call.span(), call.timer(), call.accounting());
    }

    /**
     * Shared terminal cleanup for a call leaving the queue, used both when a call is cleaned up during a drain
     * ({@link #cleanupDeferredCall}) and when its timeout fires ({@link #failWithQueueTimeout}): cancels any pending
     * timeout task, decrements the queue size at most once, and eagerly completes the span and stops the timer (both
     * idempotent).
     */
    private void completeAndDecrement(
            Optional<ScheduledFuture<?>> timeoutFuture,
            DetachedSpan span,
            IdempotentTimerContext timer,
            QueueSizeAccounting accounting) {
        timeoutFuture.ifPresent(future -> future.cancel(false));
        accounting.decrementIfCounted();
        span.complete(QueuedChannelTagTranslator.INSTANCE, this);
        timer.stop();
    }

    /**
     * Creates a copy of the DeferredCall with a fresh timeout task for the remaining budget.
     */
    private DeferredCall addTimeoutOnRequeue(DeferredCall original) {
        @Nullable
        ScheduledFuture<?> newTimeout = scheduleTimeoutFromExpiration(
                original.request(), original.response(), original.span(), original.timer(), original.accounting());
        return DeferredCall.builder()
                .from(original)
                .timeoutFuture(Optional.ofNullable(newTimeout))
                .build();
    }

    /**
     * This queue implementation requires at least one request to be executable at a time regardless of the underlying
     * limiter design, because triggering the next schedule attempt is done when a request completes. If no requests
     * are active, but requests are queued, we risk requests getting "stuck" indefinitely, until another request
     * attempt is made, which is not guaranteed.
     * So, when no requests are in flight in the delegate, we explicitly bypass limits to ensure at least one
     *
     */
    private LimitEnforcement limitEnforcement() {
        return inFlight.get() <= 0 ? LimitEnforcement.DANGEROUS_BYPASS_LIMITS : LimitEnforcement.DEFAULT_ENABLED;
    }

    private SafeIllegalStateException limitEnforcementExpectationFailure(Endpoint endpoint) {
        return new SafeIllegalStateException(
                "A request which explicitly bypassed rate limits failed to execute, which "
                        + "violates the requirements of the QueuedChannel. Please report this to "
                        + "the Dialogue maintainers!",
                SafeArg.of("channel", channelName),
                SafeArg.of("service", endpoint.serviceName()),
                SafeArg.of("endpoint", endpoint.endpointName()));
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
        public void onSuccess(@Nullable Response result) {
            // decrementing inflight must occur prior to calling schedule, ensuring that
            // schedule may be called after inflight is returned to zero.
            inFlight.decrementAndGet();
            if (result != null && !response.set(result)) {
                result.close();
            }
            schedule();
        }

        @Override
        public void onFailure(Throwable throwable) {
            // decrementing inflight must occur prior to calling schedule, ensuring that
            // schedule may be called after inflight is returned to zero.
            inFlight.decrementAndGet();
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

    /**
     * Wraps a {@link Timer.Context} to make {@link #stop()} idempotent. The underlying {@link Timer.Context#stop()}
     * records the duration on every call. This wrapper ensures only the first call records.
     */
    static final class IdempotentTimerContext {
        private final Timer.Context delegate;
        private final AtomicBoolean stopped = new AtomicBoolean();

        IdempotentTimerContext(Timer.Context delegate) {
            this.delegate = delegate;
        }

        void stop() {
            if (stopped.compareAndSet(false, true)) {
                delegate.stop();
            }
        }
    }

    @Value.Immutable
    interface DeferredCall {
        Endpoint endpoint();

        Request request();

        SettableFuture<Response> response();

        DetachedSpan span();

        IdempotentTimerContext timer();

        /** The scheduled timeout task, if queue timeout is enabled. Cancelled on dispatch. */
        Optional<ScheduledFuture<?>> timeoutFuture();

        QueueSizeAccounting accounting();

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

        List<SafeArg<?>> queueFullSafeArgs();

        Counter requestQueueTimeout();
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

            @Override
            public List<SafeArg<?>> queueFullSafeArgs() {
                return List.of();
            }

            @Override
            public Counter requestQueueTimeout() {
                return metrics.requestQueueTimeout(channelName);
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

            @Override
            public List<SafeArg<?>> queueFullSafeArgs() {
                return List.of(SafeArg.of("sticky", true));
            }

            @Override
            public Counter requestQueueTimeout() {
                return metrics.requestQueueTimeout(channelName);
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

            @Override
            public List<SafeArg<?>> queueFullSafeArgs() {
                return List.of(SafeArg.of("service", service), SafeArg.of("endpoint", endpoint));
            }

            @Override
            public Counter requestQueueTimeout() {
                return metrics.requestQueueTimeout(channelName);
            }
        };
    }

    private static final class MemoizedQueuedChannelInstrumentation implements QueuedChannelInstrumentation {

        private final Supplier<Counter> requestsQueuedSupplier;
        private final Supplier<Timer> requestQueuedTimeSupplier;
        private final Supplier<List<SafeArg<?>>> queueFullSafeArgs;
        private final Supplier<Counter> requestQueueTimeoutSupplier;

        MemoizedQueuedChannelInstrumentation(QueuedChannelInstrumentation delegate) {
            this.requestsQueuedSupplier = Suppliers.memoize(delegate::requestsQueued);
            this.requestQueuedTimeSupplier = Suppliers.memoize(delegate::requestQueuedTime);
            this.queueFullSafeArgs = Suppliers.memoize(delegate::queueFullSafeArgs);
            this.requestQueueTimeoutSupplier = Suppliers.memoize(delegate::requestQueueTimeout);
        }

        @Override
        public Counter requestsQueued() {
            return requestsQueuedSupplier.get();
        }

        @Override
        public Timer requestQueuedTime() {
            return requestQueuedTimeSupplier.get();
        }

        @Override
        public List<SafeArg<?>> queueFullSafeArgs() {
            return queueFullSafeArgs.get();
        }

        @Override
        public Counter requestQueueTimeout() {
            return requestQueueTimeoutSupplier.get();
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
