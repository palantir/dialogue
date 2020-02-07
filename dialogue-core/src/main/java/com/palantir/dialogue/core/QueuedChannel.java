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
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.immutables.value.Value;

/**
 * A {@link Channel} that queues requests while the underlying {@link LimitedChannel} is unable to accept any new
 * requests. This is done by enqueueing requests on submission, and then running the schedule loop in one of 3 ways:
 * <ol>
 *     <li>On submission - allows execution when there is available capacity</li>
 *     <li>On request completion - allows execution when capacity has now become available</li>
 *     <li>Periodically (eg: every 100ms) - allows execution when there may have been no capaciy and no in-flight
 *     requests</li>
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
final class QueuedChannel implements Channel {

    private static final Executor DIRECT = MoreExecutors.directExecutor();

    private final BlockingDeque<DeferredCall> queuedCalls;
    private final LimitedChannel delegate;
    // Tracks requests that are current executing in delegate and are not tracked in queuedCalls
    private final AtomicInteger numRunningRequests = new AtomicInteger(0);

    QueuedChannel(LimitedChannel channel, DispatcherMetrics metrics) {
        this(channel, 1_000, metrics);
    }

    @VisibleForTesting
    @SuppressWarnings("FutureReturnValueIgnored")
    QueuedChannel(LimitedChannel delegate, int maxQueueSize, DispatcherMetrics metrics) {
        this.delegate = delegate;
        this.queuedCalls = new LinkedBlockingDeque<>(maxQueueSize);

        metrics.callsQueued(queuedCalls::size);
        metrics.callsRunning(numRunningRequests::get);
    }

    /**
     * Enqueues and tries to schedule as many queued tasks as possible.
     */
    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        // Optimistically avoid the queue in the fast path.
        // Queuing adds contention between threads and should be avoided unless we need to shed load.
        Optional<ListenableFuture<Response>> maybeResult = delegate.maybeExecute(endpoint, request);
        if (maybeResult.isPresent()) {
            ListenableFuture<Response> result = maybeResult.get();
            numRunningRequests.incrementAndGet();
            result.addListener(this::onCompletion, DIRECT);
            return result;
        }

        DeferredCall components = ImmutableDeferredCall.of(endpoint, request, SettableFuture.create());

        if (!queuedCalls.offer(components)) {
            return Futures.immediateFuture(RateLimitedResponse.INSTANCE);
        }

        schedule();

        return components.response();
    }

    private void onCompletion() {
        numRunningRequests.decrementAndGet();
        schedule();
    }

    /**
     * Try to schedule as many tasks as possible. Called when requests are submitted and when they complete.
     */
    private void schedule() {
        while (scheduleNextTask()) {
            // Do nothing
        }
    }

    /**
     * Get the next call and attempt to execute it. If it is runnable, wire up the underlying future to the one
     * previously returned to the caller. If it is not runnable, add it back into the queue. Returns true if more
     * tasks may be able to be scheduled, and false otherwise.
     */
    private boolean scheduleNextTask() {
        DeferredCall components = queuedCalls.poll();
        if (components == null) {
            return false;
        }

        Optional<ListenableFuture<Response>> response =
                delegate.maybeExecute(components.endpoint(), components.request());

        if (response.isPresent()) {
            numRunningRequests.incrementAndGet();
            response.get().addListener(numRunningRequests::decrementAndGet, DIRECT);
            Futures.addCallback(response.get(), new ForwardAndSchedule(components.response()), DIRECT);
            return true;
        } else {
            queuedCalls.addFirst(components);
            return false;
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
            response.set(result);
            schedule();
        }

        @Override
        public void onFailure(Throwable throwable) {
            response.setException(throwable);
            schedule();
        }
    }

    private enum RateLimitedResponse implements Response {
        INSTANCE;

        @Override
        public InputStream body() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int code() {
            return 429;
        }

        @Override
        public Map<String, List<String>> headers() {
            return ImmutableMap.of();
        }
    }

    @Value.Immutable
    interface DeferredCall {
        @Value.Parameter
        Endpoint endpoint();

        @Value.Parameter
        Request request();

        @Value.Parameter
        SettableFuture<Response> response();
    }
}
