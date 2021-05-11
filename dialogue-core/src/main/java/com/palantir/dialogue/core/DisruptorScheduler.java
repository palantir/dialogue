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
import com.codahale.metrics.Timer.Context;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.QueuedChannel.QueuedChannelInstrumentation;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.DetachedSpan;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Enclosing
final class DisruptorScheduler implements Channel {

    private static final Logger log = LoggerFactory.getLogger(DisruptorScheduler.class);

    // Tracks requests that are current executing in delegate and are not tracked in queuedCalls
    private final AtomicInteger queueSizeEstimate = new AtomicInteger(0);

    private final int maxQueueSize;
    private final NeverThrowLimitedChannel delegate;
    private final String channelName;
    private final Supplier<Counter> queueSizeCounter;
    private final Timer queuedTime;
    private final Supplier<ListenableFuture<Response>> limitedResultSupplier;
    // Metrics aren't reported until the queue is first used, allowing per-endpoint queues to
    // avoid creating unnecessary data.
    private volatile boolean shouldRecordQueueMetrics;
    private final EventHandlerImpl eventHandler;
    private final RingBuffer<QueueEvent> ringBuffer;

    private DisruptorScheduler(
            LimitedChannel delegate, String channelName, QueuedChannelInstrumentation metrics, int maxQueueSize) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.channelName = channelName;
        this.maxQueueSize = maxQueueSize;
        // Lazily create the counter. Unlike meters, timers, and histograms, counters cannot be ignored when they have
        // zero interactions because they support both increment and decrement operations.
        this.queueSizeCounter = Suppliers.memoize(metrics::requestsQueued);
        this.queuedTime = metrics.requestQueuedTime();
        this.limitedResultSupplier = () -> Futures.immediateFailedFuture(new SafeRuntimeException(
                "Unable to make a request (queue is full)", SafeArg.of("maxQueueSize", maxQueueSize)));

        // TODO(12345): Safely shutdown?
        // TODO(12345): Even better: should only have one per JVM.
        Disruptor<QueueEvent> disruptor = new Disruptor<>(
                QueueEvent::new,
                // Probably overkill
                16_000,
                createThreadFactory(),
                ProducerType.MULTI,
                // Probably don't want blocking? But easiest to start with
                new BlockingWaitStrategy());
        eventHandler = new EventHandlerImpl();
        disruptor.handleEventsWith(eventHandler).;
        disruptor.start();
        ringBuffer = disruptor.getRingBuffer();
    }

    public static Channel create(Config cf, LimitedChannel delegate) {
        return new DisruptorScheduler(
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
        if (queueSizeEstimate.get() <= 0) {
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
        if (queueSizeEstimate.get() >= maxQueueSize) {
            return Optional.empty();
        }

        shouldRecordQueueMetrics = true;

        // TODO(12345): I don't think we can do this without allocating, in the 1 disruptor case we need more than 3
        // args.
        SettableFuture<Response> response = SettableFuture.create();
        DetachedSpan span = DetachedSpan.start("Dialogue-request-enqueued");
        Context timer = queuedTime.time();
        ringBuffer.publishEvent((event, _sequence) -> {
            event.eventType = QueueEventType.ENQUEUE;
            event.endpoint = endpoint;
            event.request = request;
            event.response = response;
            event.span = span;
            event.timer = timer;
        });

        int newSize = incrementQueueSize();

        if (log.isDebugEnabled()) {
            log.debug(
                    "Request queued {} on channel {}",
                    SafeArg.of("queueSize", newSize),
                    SafeArg.of("channelName", channelName));
        }

        return Optional.of(response);
    }

    private void onCompletion() {
        ringBuffer.publishEvent(CompletionEventTranslator.INSTANCE);
    }

    private int incrementQueueSize() {
        queueSizeCounter.get().inc();
        return queueSizeEstimate.incrementAndGet();
    }

    @Override
    public String toString() {
        return "QueuedChannel{queueSizeEstimate="
                + queueSizeEstimate + ", maxQueueSize="
                + maxQueueSize + ", delegate="
                + delegate + '}';
    }

    private enum CompletionEventTranslator implements EventTranslator<QueueEvent> {
        INSTANCE;

        @Override
        public void translateTo(QueueEvent event, long _sequence) {
            event.eventType = QueueEventType.COMPLETION;
        }
    }

    private static final class EventHandlerImpl implements EventHandler<QueueEvent> {

        private final Map<QueueKey, Queue<DeferredCall>> queues = new HashMap<>();

        @Override
        @SuppressWarnings("NullAway")
        public void onEvent(QueueEvent event, long _sequence, boolean endOfBatch) {
            try {
                if (event.eventType.isEnqueue()) {
                    Request request = event.request;
                    Optional<UUID> routingKey = Optional.ofNullable(
                            request.attachments().getOrDefault(RoutingAttachments.ROUTING_KEY, null));
                    Optional<Integer> hostKey =
                            Optional.ofNullable(request.attachments().getOrDefault(RoutingAttachments.HOST_KEY, null));
                    QueueKey queueKey;
                    if (!routingKey.isPresent() && !hostKey.isPresent()) {
                        queueKey = ImmutableDisruptorScheduler.QueueKey.of();
                    } else {
                        queueKey = ImmutableDisruptorScheduler.QueueKey.of(routingKey, hostKey);
                    }

                    Queue<DeferredCall> deferredCalls = queues.get(queueKey);
                    if (deferredCalls == null) {
                        deferredCalls = new ArrayDeque<>();
                        queues.put(queueKey, deferredCalls);
                    }

                    deferredCalls.add(ImmutableDisruptorScheduler.DeferredCall.of(event.endpoint, event.request, event.response, event.span, event.timer));
                } else {
                    // NOOP; we'll simply fall through to do the scheduling at end of batch.
                    // TODO(12345): Possibly could route cheaper/better if we know what hosts the requests were executed
                    // on. But for now let's keep this scheduler dumb/oblivious of what it's scheduling on/only able to
                    // poll the shared resource (downstream LimitedChannel);
                }

                if (endOfBatch) {
                    // TODO(12345): Actually do the routing and polling of downstream channel.
                }
            } finally {
                event.clear();
            }
        }
    }

    private static final class QueueEvent {
        // Events can either be enqueues, in which case all the following fields are set, or completions,
        // in which all the following fields are null and should be ignored
        @Nullable
        private QueueEventType eventType = null;

        @Nullable
        private Endpoint endpoint = null;

        @Nullable
        private Request request = null;

        @Nullable
        private SettableFuture<Response> response = null;

        @Nullable
        private DetachedSpan span = null;

        @Nullable
        private Timer.Context timer = null;

        private void clear() {
            eventType = null;
            endpoint = null;
            request = null;
            response = null;
            span = null;
            timer = null;
        }
    }

    private enum QueueEventType {
        ENQUEUE,
        COMPLETION;

        boolean isEnqueue() {
            return this == ENQUEUE;
        }
    }

    @Value.Immutable(singleton = true)
    interface QueueKey {
        @Value.Parameter
        Optional<UUID> routingKey();

        @Value.Parameter
        Optional<Integer> hostKey();
    }

    @Value.Immutable
    interface DeferredCall {
        @Value.Parameter
        Endpoint endpoint();

        @Value.Parameter
        Request request();

        @Value.Parameter
        SettableFuture<Response> response();

        @Value.Parameter
        DetachedSpan span();

        @Value.Parameter
        Timer.Context timer();
    }

    private static ThreadFactory createThreadFactory() {
        return new ThreadFactoryBuilder().setNameFormat("Dialogue-scheduler-%d").build();
    }
}
