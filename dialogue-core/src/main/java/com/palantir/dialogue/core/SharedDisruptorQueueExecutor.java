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

import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.SharedDisruptorQueueExecutor.QueueEvent;
import com.palantir.tracing.DetachedSpan;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nullable;

final class SharedDisruptorQueueExecutor implements EventHandler<QueueEvent>, QueueExecutor {

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactoryBuilder()
            .setNameFormat("Dialogue-queue-scheduler-%d")
            .build();
    private static final int RING_BUFFER_SIZE = 16_384;
    private static final QueueExecutor INSTANCE = new SharedDisruptorQueueExecutor();

    private final Set<EventProcessor> allProcessors;
    // Set of processors that should run dispatch.
    private final Set<EventProcessor> dirtyProcessors;
    private final RingBuffer<QueueEvent> ringBuffer;

    private SharedDisruptorQueueExecutor() {
        // We want the set to be keyed by instances of EventProcessor, of which there is 1 per DisruptorScheduler
        // instance. Additionally, we want to only keep weak references to those, so that we don't leak queues for
        // GCed DialogueChannels.
        allProcessors = Collections.newSetFromMap(new WeakHashMap<>(new IdentityHashMap<>()));
        // This set is cleared every batch, so can use strong keys.
        dirtyProcessors = Collections.newSetFromMap(new IdentityHashMap<>());
        Disruptor<QueueEvent> disruptor = new Disruptor<>(
                QueueEvent::new,
                // Probably overkill, but keeping the same size as Log4j AsyncLogger queue.
                RING_BUFFER_SIZE,
                THREAD_FACTORY,
                ProducerType.MULTI,
                // Probably don't need blocking? But easiest to start with
                new BlockingWaitStrategy());
        disruptor.handleEventsWith(this);
        disruptor.start();
        ringBuffer = disruptor.getRingBuffer();
    }

    @Override
    public void requestComplete(QueueExecutor.EventProcessor eventProcessor) {
        ringBuffer.publishEvent(this::toCompletion, eventProcessor);
    }

    @Override
    public void enqueue(
            EventProcessor processor,
            Request request,
            Endpoint endpoint,
            SettableFuture<Response> responseFuture,
            DetachedSpan span,
            Timer.Context queuedTimeTimer) {

        // Using raw APIs because number of args > 3
        long sequence = ringBuffer.next(); // Grab the next sequence
        try {
            QueueEvent event = ringBuffer.get(sequence); // Get the entry in the Disruptor
            event.enqueue(processor, endpoint, request, responseFuture, span, queuedTimeTimer);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    @Override
    @SuppressWarnings("NullAway")
    public void onEvent(QueueEvent event, long _sequence, boolean endOfBatch) {
        try {
            EventProcessor currentEventProcessor = event.eventProcessor;
            allProcessors.add(currentEventProcessor);

            // A scheduler needs to reschedule even IFF it did not get any completions.
            // This is because if, when the channel is created, the downstream channel is broken and we have to
            // queue we will build up a queue and never clear it: since we never managed to submit a request,
            // nothing is going to poke the scheduler.
            // There is probably a better way to solve this.
            dirtyProcessors.add(currentEventProcessor);
            if (event.eventType.isEnqueue()) {
                currentEventProcessor.enqueueRequest(QueueExecutor.deferredCall(
                        event.endpoint, event.request, event.response, event.span, event.queuedTimeTimer));
            }

            if (endOfBatch) {
                dirtyProcessors.forEach(EventProcessor::dispatchRequests);
                dirtyProcessors.clear();
            }
        } finally {
            event.clear();
        }
    }

    @Override
    public boolean isEmpty() {
        return ringBuffer.remainingCapacity() == RING_BUFFER_SIZE;
    }

    private void toCompletion(QueueEvent event, long _sequence, QueueExecutor.EventProcessor eventProcessor) {
        event.completion(eventProcessor);
    }

    static QueueExecutor instance() {
        return INSTANCE;
    }

    static final class QueueEvent {
        @Nullable
        private QueueEventType eventType = null;

        @Nullable
        private QueueExecutor.EventProcessor eventProcessor = null;

        @Nullable
        private Endpoint endpoint = null;

        @Nullable
        private Request request = null;

        @Nullable
        private SettableFuture<Response> response = null;

        @Nullable
        private DetachedSpan span = null;

        @Nullable
        private Timer.Context queuedTimeTimer = null;

        private void enqueue(
                QueueExecutor.EventProcessor newEventProcessor,
                Endpoint newEndpoint,
                Request newRequest,
                SettableFuture<Response> newResponse,
                DetachedSpan newSpan,
                Timer.Context newQueuedTimeTimer) {
            this.eventType = QueueEventType.ENQUEUE;
            this.eventProcessor = newEventProcessor;
            this.endpoint = newEndpoint;
            this.request = newRequest;
            this.response = newResponse;
            this.span = newSpan;
            this.queuedTimeTimer = newQueuedTimeTimer;
        }

        private void completion(EventProcessor newEventProcessor) {
            this.eventType = QueueEventType.COMPLETION;
            this.eventProcessor = newEventProcessor;
        }

        private void clear() {
            eventType = null;
            endpoint = null;
            request = null;
            response = null;
            span = null;
            queuedTimeTimer = null;
        }
    }

    private enum QueueEventType {
        ENQUEUE,
        COMPLETION;

        boolean isEnqueue() {
            return this == ENQUEUE;
        }
    }
}
