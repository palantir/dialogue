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
import com.lmax.disruptor.EventTranslatorOneArg;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
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

    private static final MultiplexingEventHandler MULTIPLEXING_EVENT_HANDLER = new MultiplexingEventHandler();
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
        eventHandler = new EventHandlerImpl();
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

        SettableFuture<Response> response = MULTIPLEXING_EVENT_HANDLER.enqueue(this, request, endpoint);
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
        MULTIPLEXING_EVENT_HANDLER.onCompletion(this);
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

    private enum CompletionEventTranslator implements EventTranslatorOneArg<QueueEvent, DisruptorScheduler> {
        INSTANCE;

        @Override
        public void translateTo(QueueEvent event, long _sequence, DisruptorScheduler scheduler) {
            event.eventType = QueueEventType.COMPLETION;
            event.eventHandlerImpl = scheduler.eventHandler;
        }
    }

    private static final class MultiplexingEventHandler implements EventHandler<QueueEvent> {

        private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
                .setNameFormat("Dialogue-queue-scheduler-%d")
                .build();

        private final Set<EventHandlerImpl> eventHandlers;
        private final RingBuffer<QueueEvent> ringBuffer;

        MultiplexingEventHandler() {
            // We want the set to be keyed by instances of EventHandlerImpl, of which there is 1 per DisruptorScheduler
            // instance. Additionally, we want to only keep weak references to those, so that we don't leak queues for
            // GCed DialogueChannels.
            eventHandlers = Collections.newSetFromMap(new WeakHashMap<>(new IdentityHashMap<>()));
            Disruptor<QueueEvent> disruptor = new Disruptor<>(
                    QueueEvent::new,
                    // Probably overkill, but keeping the same size as Log4j AsyncLogger queue.
                    16_384,
                    THREAD_FACTORY,
                    ProducerType.MULTI,
                    // Probably don't need blocking? But easiest to start with
                    new BlockingWaitStrategy());
            disruptor.handleEventsWith(this);
            disruptor.start();
            ringBuffer = disruptor.getRingBuffer();
        }

        private void onCompletion(DisruptorScheduler scheduler) {
            ringBuffer.publishEvent(CompletionEventTranslator.INSTANCE, scheduler);
        }

        private SettableFuture<Response> enqueue(DisruptorScheduler scheduler, Request request, Endpoint endpoint) {
            SettableFuture<Response> response = SettableFuture.create();
            DetachedSpan span = DetachedSpan.start("Dialogue-request-enqueued");
            Context timer = scheduler.queuedTime.time();

            // Using raw APIs because number of args > 3
            long sequence = ringBuffer.next(); // Grab the next sequence
            try {
                QueueEvent event = ringBuffer.get(sequence); // Get the entry in the Disruptor
                event.eventType = QueueEventType.ENQUEUE;
                event.endpoint = endpoint;
                event.request = request;
                event.response = response;
                event.span = span;
                event.timer = timer;
            } finally {
                ringBuffer.publish(sequence);
            }
            return response;
        }

        @Override
        @SuppressWarnings("NullAway")
        public void onEvent(QueueEvent event, long sequence, boolean endOfBatch) {
            try {
                EventHandlerImpl currentEventHandler = event.eventHandlerImpl;
                eventHandlers.add(currentEventHandler);
                currentEventHandler.onEvent(event, sequence, endOfBatch);
            } finally {
                event.clear();
            }
        }
    }

    private final class EventHandlerImpl implements EventHandler<QueueEvent> {

        // Always iterate in the same order for fairness and keep weak references only.
        private final Map<QueueKey, Queue<DeferredCall>> queues = new LinkedHashMap<>();

        @Override
        @SuppressWarnings("NullAway")
        public void onEvent(QueueEvent event, long _sequence, boolean endOfBatch) {
            if (event.eventType.isEnqueue()) {
                enqueue(event);
            }

            // On completion event we'll simply fall through to do the scheduling at end of batch.
            // TODO(12345): Possibly could route cheaper/better if we know what hosts the requests were executed
            // on. But for now let's keep this scheduler dumb/oblivious of what it's scheduling on/only able to
            // poll the shared resource (downstream LimitedChannel);

            if (endOfBatch) {
                schedule();
            }
        }

        @SuppressWarnings("NullAway")
        private void enqueue(QueueEvent event) {
            Request request = event.request;
            UUID routingKey = request.attachments().getOrDefault(RoutingAttachments.ROUTING_KEY, null);
            Integer hostKey = request.attachments().getOrDefault(RoutingAttachments.HOST_KEY, null);
            QueueKey queueKey;
            if (routingKey == null && hostKey == null) {
                queueKey = ImmutableDisruptorScheduler.QueueKey.of();
            } else {
                queueKey = ImmutableDisruptorScheduler.QueueKey.of(routingKey, hostKey);
            }

            Queue<DeferredCall> deferredCalls = queues.get(queueKey);
            if (deferredCalls == null) {
                deferredCalls = new ArrayDeque<>();
                queues.put(queueKey, deferredCalls);
            }

            deferredCalls.add(ImmutableDisruptorScheduler.DeferredCall.of(
                    event.endpoint, event.request, event.response, event.span, event.timer));
        }

        private void schedule() {
            int numScheduled = 0;
            boolean didWorkInPass;

            do {
                didWorkInPass = false;

                // TODO(12345): Figure out how to iterate more stably here.

                for (Queue<DeferredCall> queue : queues.values()) {
                    DeferredCall peekedCall = queue.peek();
                    if (peekedCall != null) {
                        Optional<ListenableFuture<Response>> maybeScheduled =
                                DisruptorScheduler.this.delegate.maybeExecute(
                                        peekedCall.endpoint(), peekedCall.request());

                        if (maybeScheduled.isPresent()) {
                            queue.remove();
                            didWorkInPass = true;
                            numScheduled += 1;
                        }
                    }
                }
            } while (didWorkInPass);

            if (log.isDebugEnabled()) {
                log.debug(
                        "Scheduled {} requests on channel {}",
                        SafeArg.of("numScheduled", numScheduled),
                        SafeArg.of("channelName", DisruptorScheduler.this.channelName));
            }
        }
    }

    private static final class QueueEvent {
        // Events can either be enqueues, in which case all the following fields are set, or completions,
        // in which all the following fields are null and should be ignored
        @Nullable
        private QueueEventType eventType = null;

        @Nullable
        private EventHandlerImpl eventHandlerImpl;

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
        @Nullable
        @Value.Parameter
        UUID routingKey();

        @Nullable
        @Value.Parameter
        Integer hostKey();
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
}
