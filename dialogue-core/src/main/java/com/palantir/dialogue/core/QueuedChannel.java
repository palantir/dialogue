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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import org.immutables.value.Value;

final class QueuedChannel implements Channel {

    private static final Executor DIRECT = MoreExecutors.directExecutor();
    private final Deque<CallComponents> queuedCalls = new ConcurrentLinkedDeque<>();
    private final LimitedChannel delegate;

    QueuedChannel(LimitedChannel delegate) {
        this.delegate = delegate;
    }

    /**
     * Enqueue the call and try to schedule as many queued tasks as possible.
     */
    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        CallComponents components = ImmutableCallComponents.of(endpoint, request, SettableFuture.create());

        if (!queuedCalls.offer(components)) {
            throw QosException.unavailable();
        }

        schedule();

        return components.response();
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
        CallComponents components = queuedCalls.poll();
        if (components == null) {
            return false;
        }

        Optional<ListenableFuture<Response>> response =
                delegate.maybeExecute(components.endpoint(), components.request());

        if (response.isPresent()) {
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

    @Value.Immutable
    interface CallComponents {
        @Value.Parameter Endpoint endpoint();
        @Value.Parameter Request request();
        @Value.Parameter SettableFuture<Response> response();
    }
}
