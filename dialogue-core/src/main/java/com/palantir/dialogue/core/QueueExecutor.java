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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tracing.DetachedSpan;
import org.immutables.value.Value;

@Value.Enclosing
interface QueueExecutor {

    void enqueue(
            QueueExecutor.EventProcessor processor,
            Request request,
            Endpoint endpoint,
            SettableFuture<Response> responseFuture,
            DetachedSpan span,
            Timer.Context queuedTimeTimer);

    void requestComplete(QueueExecutor.EventProcessor eventProcessor);

    @VisibleForTesting
    boolean isEmpty();

    // Calls will happen on a single thread.
    interface EventProcessor {
        void enqueueRequest(QueueExecutor.DeferredCall deferredCall);

        int dispatchRequests();
    }

    static DeferredCall deferredCall(
            Endpoint endpoint,
            Request request,
            SettableFuture<Response> response,
            DetachedSpan span,
            Timer.Context timer) {
        return ImmutableQueueExecutor.DeferredCall.of(endpoint, request, response, span, timer);
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
