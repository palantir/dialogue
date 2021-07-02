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
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingAttachments;
import com.palantir.dialogue.RoutingAttachments.RoutingKey;
import com.palantir.dialogue.core.QueuedChannel.DeferredCall;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StickyQueueChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(StickyQueueChannel.class);

    private final String channelName;
    private final IntSupplier maxInFlightRequests;
    private final int maxQueueSize;
    private final QueuedChannelInstrumentation instrumentation;
    private final Supplier<ListenableFuture<Response>> limitedResultSupplier;
    private final Channel delegate;
    // How do I evict stuff here?
    private final LoadingCache<RoutingKey, Queue> queues;

    // Shared state
    private final AtomicInteger totalQueuesSizeEstimate = new AtomicInteger(0);

    StickyQueueChannel(
            Channel delegate,
            String channelName,
            IntSupplier maxInFlightRequests,
            int maxQueueSize,
            QueuedChannelInstrumentation instrumentation) {
        this.maxInFlightRequests = maxInFlightRequests;
        this.channelName = channelName;
        this.maxQueueSize = maxQueueSize;
        this.instrumentation = instrumentation;
        this.limitedResultSupplier = () -> Futures.immediateFailedFuture(new SafeRuntimeException(
                "Unable to make a request (queue is full)", SafeArg.of("maxQueueSize", maxQueueSize)));
        this.delegate = delegate;
        queues = Caffeine.newBuilder().build(_ignore -> new Queue());
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        RoutingKey nullableRoutingKey = request.attachments().getOrDefault(RoutingAttachments.ROUTING_KEY, null);
        if (nullableRoutingKey == null) {
            return delegate.execute(endpoint, request);
        } else {
            return queues.get(nullableRoutingKey)
                    .maybeExecute(endpoint, request)
                    .orElseGet(limitedResultSupplier);
        }
    }

    static StickyQueueChannel create(Config cf, Endpoint endpoint, Channel delegate) {
        DialogueClientMetrics metrics = DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry());
        String channelName = cf.channelName();
        String serviceName = endpoint.serviceName();

        Counter requestsQueued = metrics.requestsStickyQueued()
                .channelName(channelName)
                .serviceName(serviceName)
                .build();
        Timer requestsQueuedTime = metrics.requestStickyQueuedTime()
                .channelName(channelName)
                .serviceName(serviceName)
                .build();

        return new StickyQueueChannel(
                delegate, channelName, () -> 20, cf.maxQueueSize(), new QueuedChannelInstrumentation() {
                    @Override
                    public Counter requestsQueued() {
                        return requestsQueued;
                    }

                    @Override
                    public Timer requestQueuedTime() {
                        return requestsQueuedTime;
                    }
                });
    }

    private final class Queue implements LimitedChannel {

        private AtomicInteger inFlightRequests = new AtomicInteger();
        private final Deque<QueuedChannel.DeferredCall> queuedCalls = new ConcurrentLinkedDeque<>();

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
            return executeOrQueue(endpoint, request).map(responseFuture -> {
                return responseFuture;
            });
        }

        private Optional<ListenableFuture<Response>> executeOrQueue(Endpoint endpoint, Request request) {
            // Probably want a limit to number of iterations here?
            while (true) {
                int maxInFlight = maxInFlightRequests.getAsInt();
                int currentInFlights = inFlightRequests.get();
                if (currentInFlights < maxInFlight) {
                    if (inFlightRequests.compareAndSet(currentInFlights, currentInFlights + 1)) {
                        return Optional.of(delegate.execute(endpoint, request));
                    }
                } else {
                    if (totalQueuesSizeEstimate.get() >= maxQueueSize) {
                        return Optional.empty();
                    }

                    QueuedChannel.DeferredCall components = QueuedChannel.DeferredCall.builder()
                            .endpoint(endpoint)
                            .request(request)
                            .response(SettableFuture.create())
                            .span(DetachedSpan.start("Dialogue-request-enqueued"))
                            .timer(instrumentation.requestQueuedTime().time())
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
            }
        }

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
                queueHead.span().complete();
                queueHead.timer().stop();
                return true;
            }
            try (CloseableSpan ignored = queueHead.span().childSpan("Dialogue-request-scheduled")) {
                Endpoint endpoint = queueHead.endpoint();
                // Optional<ListenableFuture<Response>> maybeResponse =
                //         delegate.maybeExecute(endpoint, queueHead.request());
                ListenableFuture<Response> response = delegate.execute(endpoint, queueHead.request());

                // if (maybeResponse.isPresent()) {
                decrementQueueSize();
                // ListenableFuture<Response> response = maybeResponse.get();
                queueHead.span().complete();
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
                // } else {
                //     if (!queuedCalls.offerFirst(queueHead)) {
                //         // Should never happen, ConcurrentLinkedDeque has no maximum size
                //         log.error(
                //                 "Failed to add an attempted call back to the deque",
                //                 SafeArg.of("channel", channelName),
                //                 SafeArg.of("service", endpoint.serviceName()),
                //                 SafeArg.of("endpoint", endpoint.endpointName()));
                //         decrementQueueSize();
                //         queueHead.timer().stop();
                //         if (!queuedResponse.setException(new SafeRuntimeException(
                //                 "Failed to req-queue request",
                //                 SafeArg.of("channel", channelName),
                //                 SafeArg.of("service", endpoint.serviceName()),
                //                 SafeArg.of("endpoint", endpoint.endpointName())))) {
                //             log.debug(
                //                     "Queued response has already been completed",
                //                     SafeArg.of("channel", channelName),
                //                     SafeArg.of("service", endpoint.serviceName()),
                //                     SafeArg.of("endpoint", endpoint.endpointName()));
                //         }
                //     }
                //     return false;
                // }
            }
        }

        private int incrementQueueSize() {
            instrumentation.requestsQueued().inc();
            return StickyQueueChannel.this.totalQueuesSizeEstimate.incrementAndGet();
        }

        private void decrementQueueSize() {
            StickyQueueChannel.this.totalQueuesSizeEstimate.decrementAndGet();
            instrumentation.requestsQueued().dec();
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
    }

    interface QueuedChannelInstrumentation {
        Counter requestsQueued();

        Timer requestQueuedTime();
    }
}
