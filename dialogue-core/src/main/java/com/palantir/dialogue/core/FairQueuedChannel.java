/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.base.MoreObjects;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingAttachments;
import com.palantir.dialogue.RoutingAttachments.HostId;
import com.palantir.dialogue.core.QueueExecutor.EventProcessor;
import com.palantir.dialogue.core.QueuedChannel.QueuedChannelInstrumentation;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Enclosing
public final class FairQueuedChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(FairQueuedChannel.class);
    private static final Supplier<FairQueue> FAIR_QUEUE_SUPPLIER = FairQueue1::new;

    private final QueueExecutor queueExecutor;
    private final QueueSize queueSize;
    private final NeverThrowLimitedChannel delegate;
    private final String channelName;
    private final Timer queuedTime;
    private final Supplier<ListenableFuture<Response>> limitedResultSupplier;
    // Metrics aren't reported until the queue is first used, allowing per-endpoint queues to
    // avoid creating unnecessary data.
    private volatile boolean shouldRecordQueueMetrics;
    private final EventProcessor eventProcessor;

    @VisibleForTesting
    FairQueuedChannel(
            QueueExecutor queueExecutor,
            LimitedChannel delegate,
            String channelName,
            QueuedChannelInstrumentation metrics,
            int maxQueueSize) {
        this.queueExecutor = queueExecutor;
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.channelName = channelName;
        this.queueSize = new QueueSize(metrics, maxQueueSize);

        this.queuedTime = metrics.requestQueuedTime();
        this.limitedResultSupplier = () -> Futures.immediateFailedFuture(new SafeRuntimeException(
                "Unable to make a request (queue is full)", SafeArg.of("maxQueueSize", maxQueueSize)));
        eventProcessor = new EventProcessorImpl(
                this.delegate, channelName, this::onCompletion, queueSize, FAIR_QUEUE_SUPPLIER.get());
    }

    public static Channel create(Config cf, LimitedChannel delegate) {
        return new FairQueuedChannel(
                cf.queueExecutor(),
                delegate,
                cf.channelName(),
                QueuedChannel.channelInstrumentation(
                        DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry()), cf.channelName()),
                cf.maxQueueSize());
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return maybeExecute(endpoint, request).orElseGet(limitedResultSupplier);
    }

    @VisibleForTesting
    Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        // Optimistically avoid the queue in the fast path.
        // Queuing adds contention between threads and should be avoided unless we need to shed load.
        if (queueSize.isEmpty()) {
            Optional<ListenableFuture<Response>> maybeResult = delegate.maybeExecute(endpoint, request);
            if (maybeResult.isPresent()) {
                ListenableFuture<Response> result = maybeResult.get();
                DialogueFutures.addDirectListener(result, this::onCompletion);
                // While the queue was avoided, this is equivalent to spending zero time on the queue.
                if (shouldRecordQueueMetrics) {
                    queuedTime.update(0, TimeUnit.NANOSECONDS);
                }
                return maybeResult;
            }
        }

        // Important to read the queue size here as well as prior to the optimistic maybeExecute because
        // maybeExecute may take sufficiently long that other requests could be queued.
        if (queueSize.isFull()) {
            return Optional.empty();
        }

        shouldRecordQueueMetrics = true;

        SettableFuture<Response> response = SettableFuture.create();
        DetachedSpan span = DetachedSpan.start("Dialogue-request-enqueued");
        Timer.Context queuedTimeTimer = queuedTime.time();
        queueExecutor.enqueue(eventProcessor, request, endpoint, response, span, queuedTimeTimer);
        int newSize = queueSize.incrementQueueSize();

        if (log.isDebugEnabled()) {
            log.debug(
                    "Request queued {} on channel {}",
                    SafeArg.of("queueSize", newSize),
                    SafeArg.of("channelName", channelName));
        }

        return Optional.of(response);
    }

    @VisibleForTesting
    void onCompletion() {
        queueExecutor.requestComplete(this.eventProcessor);
    }

    boolean allEventsProcessed() {
        return queueExecutor.isEmpty();
    }

    @Override
    public String toString() {
        return "DisruptorScheduler{queueSizeEstimate="
                + queueSize.queueSizeEstimate + ", maxQueueSize="
                + queueSize.maxQueueSize + ", delegate="
                + delegate + '}';
    }

    @VisibleForTesting
    static final class QueueSize {
        // Tracks requests that are current executing in delegate and are not tracked in queuedCalls
        private final AtomicInteger queueSizeEstimate = new AtomicInteger(0);
        private final Supplier<Counter> queueSizeCounter;
        private final int maxQueueSize;

        QueueSize(QueuedChannelInstrumentation metrics, int maxQueueSize) {
            // Lazily create the counter. Unlike meters, timers, and histograms, counters cannot be ignored when they
            // have zero interactions because they support both increment and decrement operations.
            this.queueSizeCounter = Suppliers.memoize(metrics::requestsQueued);
            this.maxQueueSize = maxQueueSize;
        }

        boolean isEmpty() {
            return queueSizeEstimate.get() <= 0;
        }

        boolean isFull() {
            return queueSizeEstimate.get() >= maxQueueSize;
        }

        private int incrementQueueSize() {
            queueSizeCounter.get().inc();
            return queueSizeEstimate.incrementAndGet();
        }

        private void decrementQueueSize() {
            queueSizeEstimate.decrementAndGet();
            queueSizeCounter.get().dec();
        }
    }

    public static final class EventProcessorImpl implements QueueExecutor.EventProcessor {

        private final NeverThrowLimitedChannel delegate;
        private final String channelName;
        private final Runnable onCompletion;
        private final QueueSize queueSize;
        private final FairQueue fairQueue;

        EventProcessorImpl(
                NeverThrowLimitedChannel delegate,
                String channelName,
                Runnable onCompletion,
                QueueSize queueSize,
                FairQueue fairQueue) {
            this.channelName = channelName;
            this.delegate = delegate;
            this.onCompletion = onCompletion;
            this.queueSize = queueSize;
            this.fairQueue = fairQueue;
        }

        @Override
        @SuppressWarnings("NullAway")
        public void enqueueRequest(QueueExecutor.DeferredCall deferredCall) {

            Request request = deferredCall.request();
            UUID routingKey = request.attachments().getOrDefault(RoutingAttachments.ROUTING_KEY, null);
            HostId hostKey = request.attachments().getOrDefault(RoutingAttachments.HOST_KEY, null);

            QueueKey queueKey;
            if (routingKey == null && hostKey == null) {
                queueKey = ImmutableFairQueuedChannel.QueueKey.of();
            } else {
                queueKey = ImmutableFairQueuedChannel.QueueKey.of(routingKey, hostKey);
            }
            fairQueue.addToQueue(queueKey, deferredCall);
        }

        @Override
        public int dispatchRequests() {
            int numDispatched = 0;
            boolean dispatchedInRound;

            do {
                try (Round round = fairQueue.startSchedulingRound()) {
                    dispatchedInRound = false;
                    while (round.hasNext()) {
                        QueueExecutor.DeferredCall head = round.next();
                        boolean scheduled = trySchedule(head);
                        if (scheduled) {
                            dispatchedInRound = true;
                            numDispatched += 1;

                            round.remove();
                        }
                    }
                }
            } while (dispatchedInRound);

            if (log.isDebugEnabled()) {
                log.debug(
                        "Scheduled {} requests on channel {}",
                        SafeArg.of("numDispatched", numDispatched),
                        SafeArg.of("channelName", channelName));
            }

            return numDispatched;
        }

        private boolean trySchedule(QueueExecutor.DeferredCall queueHead) {
            SettableFuture<Response> queuedResponse = queueHead.response();
            // If the future has been completed (most likely via cancel) the call should not be queued.
            // There's a race where cancel may be invoked between this check and execution, but the scheduled
            // request will be quickly cancelled in that case.
            if (queuedResponse.isDone()) {
                queueSize.decrementQueueSize();
                queueHead.span().complete();
                queueHead.timer().stop();
                return true;
            }

            try (CloseableSpan ignored = queueHead.span().childSpan("Dialogue-request-scheduled")) {
                Endpoint endpoint = queueHead.endpoint();
                Optional<ListenableFuture<Response>> maybeResponse =
                        delegate.maybeExecute(endpoint, queueHead.request());

                if (maybeResponse.isPresent()) {
                    queueSize.decrementQueueSize();
                    ListenableFuture<Response> response = maybeResponse.get();
                    queueHead.span().complete();
                    queueHead.timer().stop();
                    DialogueFutures.addDirectCallback(response, new ForwardAndSchedule(onCompletion, queuedResponse));
                    DialogueFutures.addDirectListener(queuedResponse, () -> {
                        if (queuedResponse.isCancelled()) {
                            // TODO(ckozak): Consider capturing the argument value provided to cancel to propagate
                            // here.
                            // Currently cancel(false) will be converted to cancel(true)
                            if (!response.cancel(true) && log.isDebugEnabled()) {
                                log.debug(
                                        "Failed to cancel delegate response, it should be reported by"
                                                + " ForwardAndSchedule logging",
                                        SafeArg.of("channel", channelName),
                                        SafeArg.of("service", endpoint.serviceName()),
                                        SafeArg.of("endpoint", endpoint.endpointName()));
                            }
                        }
                    });
                    return true;
                }
            }

            return false;
        }
    }

    interface FairQueue {
        void addToQueue(QueueKey queueKey, QueueExecutor.DeferredCall call);

        Round startSchedulingRound();
    }

    interface Round extends Iterator<QueueExecutor.DeferredCall>, AutoCloseable {
        @Override
        void close();
    }

    // Observations for less expensive scheduling algorithm (better terminating conditions):
    // 1. If you try to schedule a request without a hostKey, you can stop scheduling: all the capacity in the
    // system is exhausted.
    // 2. If you try to schedule a request with a hostKey, do not try to schedule any more requests with the same
    // hostKey.
    // With this sort of implementation, you simply keep cycling through queues, until you are no longer able to
    // schedule anything. Not quite, because you'd keep going cycling through the queue, as long as you have a
    // single host that's un-schedulable: therefore, you need to eliminate queues.
    //
    // Some drawbacks of this impl:
    // 1. If there is a lot of QueueKeys, having to copy fairQueue into SchedulingRound will get expensive.
    // Therefore it would be useful to be able to not do that.
    // 2. If you schedule something in a round, you have to go through the queues again.
    //
    // Useful data for improving this:
    // 1. Some way to eliminate queues, as soon as host is fully scheduled.
    //   * ideally without copying entire queue.
    static final class FairQueue2 implements FairQueue {

        @SuppressWarnings("StrictUnusedVariable")
        private final Map<QueueKey, Queue<QueueExecutor.DeferredCall>> allQueues = new LinkedHashMap<>();

        @SuppressWarnings("StrictUnusedVariable")
        private final Map<HostId, Integer> hostIds = new HashMap<>();

        private final Deque<QueueKey> fairQueue = new ArrayDeque<>();

        private final FairQueue2Round round = new FairQueue2Round(this);

        @Override
        public void addToQueue(QueueKey queueKey, QueueExecutor.DeferredCall call) {
            round.assertNotOpen();
            Queue<QueueExecutor.DeferredCall> deferredCalls = allQueues.get(queueKey);
            if (deferredCalls == null) {
                deferredCalls = new ArrayDeque<>();
                int numQueuesPerHost = MoreObjects.firstNonNull(hostIds.get(queueKey.hostKey()), 0) + 1;
                hostIds.put(queueKey.hostKey(), numQueuesPerHost);
                allQueues.put(queueKey, deferredCalls);
                fairQueue.addLast(queueKey);
            }

            deferredCalls.add(call);
        }

        @Override
        public Round startSchedulingRound() {
            round.startSchedulingRound();
            return round;
        }
    }

    static final class FairQueue2Round implements Round {

        private final FairQueue2 parent;
        private final Set<HostId> availableHosts = new HashSet<>();
        private final Deque<QueueKey> round = new ArrayDeque<>();

        @Nullable
        private QueueKey curKey;

        @Nullable
        private Queue<QueueExecutor.DeferredCall> curQueue;

        private boolean open = false;

        FairQueue2Round(FairQueue2 parent) {
            this.parent = parent;
        }

        void assertNotOpen() {
            Preconditions.checkState(!open, "already open!");
        }

        void startSchedulingRound() {
            assertNotOpen();
            open = true;
            round.clear();
            round.addAll(parent.fairQueue);
            availableHosts.clear();
            availableHosts.addAll(parent.hostIds.keySet());
        }

        @Override
        public boolean hasNext() {
            if (curKey != null) {
                // Item was not removed, so was not executed. Meaning the host has no more capacity;
                availableHosts.remove(curKey.hostKey());
            }

            //            boolean hasHosts = !availableHosts.isEmpty();
            //            boolean hasAnyHost = availableHosts.contains(HostId.anyHost());
            // Cannot continue if:
            // 1. No more schedulable hosts.
            // 2. Observed failure to schedule a request that could schedule on any host.
            return !round.isEmpty();
        }

        @Override
        public QueueExecutor.DeferredCall next() {
            curKey = round.remove();
            curQueue = Preconditions.checkNotNull(parent.allQueues.get(curKey), "allQueues");
            return curQueue.peek();
        }

        @Override
        public void remove() {
            curQueue().remove();

            // For fairness we remove the queue key, and add it to the end of the queue of queues if
            // it still has entries.
            parent.fairQueue.remove(curKey);

            if (curQueue().isEmpty()) {
                parent.allQueues.remove(curKey());
                parent.fairQueue.remove(curKey());
            } else {
                parent.fairQueue.add(curKey());
            }

            curQueue = null;
            curKey = null;
        }

        @Override
        public void close() {
            open = false;
            availableHosts.clear();
        }

        private QueueKey curKey() {
            return Preconditions.checkNotNull(curKey, "curKey");
        }

        private Queue<QueueExecutor.DeferredCall> curQueue() {
            return Preconditions.checkNotNull(curQueue, "curQueue");
        }
    }

    @VisibleForTesting
    static final class FairQueue1 implements FairQueue {

        private final Map<QueueKey, Queue<QueueExecutor.DeferredCall>> allQueues = new HashMap<>();
        private final Deque<QueueKey> fairQueue = new ArrayDeque<>();
        private final FairQueue1Round round = new FairQueue1Round(this);

        @Override
        public FairQueue1Round startSchedulingRound() {
            round.startSchedulingRound();
            return round;
        }

        @Override
        public void addToQueue(QueueKey queueKey, QueueExecutor.DeferredCall call) {
            round.assertNotOpen();
            Queue<QueueExecutor.DeferredCall> deferredCalls = allQueues.get(queueKey);
            if (deferredCalls == null) {
                deferredCalls = new ArrayDeque<>();
                allQueues.put(queueKey, deferredCalls);
                fairQueue.addLast(queueKey);
            }

            deferredCalls.add(call);
        }
    }

    static final class FairQueue1Round implements Round {

        private final FairQueue1 parent;
        private final Deque<QueueKey> round = new ArrayDeque<>();

        @Nullable
        private QueueKey curKey;

        @Nullable
        private Queue<QueueExecutor.DeferredCall> curQueue;

        private boolean open = false;

        FairQueue1Round(FairQueue1 parent) {
            this.parent = parent;
        }

        void assertNotOpen() {
            Preconditions.checkState(!open, "already open!");
        }

        void startSchedulingRound() {
            assertNotOpen();
            open = true;
            round.clear();
            round.addAll(parent.fairQueue);
        }

        @Override
        public boolean hasNext() {
            return !round.isEmpty();
        }

        @Override
        public QueueExecutor.DeferredCall next() {
            curKey = round.remove();
            curQueue = Preconditions.checkNotNull(parent.allQueues.get(curKey), "allQueues");
            return curQueue.peek();
        }

        @Override
        public void remove() {
            curQueue().remove();

            // For fairness we remove the queue key, and add it to the end of the queue of queues if
            // it still has entries.
            parent.fairQueue.remove(curKey);

            if (curQueue().isEmpty()) {
                parent.allQueues.remove(curKey());
                parent.fairQueue.remove(curKey());
            } else {
                parent.fairQueue.add(curKey());
            }
        }

        @Override
        public void close() {
            open = false;
            round.clear();
        }

        private QueueKey curKey() {
            return Preconditions.checkNotNull(curKey, "curKey");
        }

        private Queue<QueueExecutor.DeferredCall> curQueue() {
            return Preconditions.checkNotNull(curQueue, "curQueue");
        }
    }

    @Value.Immutable(singleton = true)
    interface QueueKey {
        @Nullable
        @Value.Parameter
        UUID routingKey();

        @Nullable
        @Value.Parameter
        HostId hostKey();
    }

    /**
     * Forward the success or failure of the call to the SettableFuture that was previously returned to the caller.
     * Also poke the disruptor to try to schedule more calls.
     */
    private static final class ForwardAndSchedule implements FutureCallback<Response> {
        private final Runnable onCompletion;
        private final SettableFuture<Response> response;

        ForwardAndSchedule(Runnable onCompletion, SettableFuture<Response> response) {
            this.onCompletion = onCompletion;
            this.response = response;
        }

        @Override
        public void onSuccess(Response result) {
            if (!response.set(result)) {
                result.close();
            }
            onCompletion.run();
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
            onCompletion.run();
        }
    }
}
