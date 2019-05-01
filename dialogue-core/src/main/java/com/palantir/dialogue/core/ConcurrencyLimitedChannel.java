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
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A channel that monitors the successes and failures of requests in order to determine the number of concurrent
 * requests allowed to a particular channel. If the channel's concurrency limit has been reach, the call will not be
 * executed.
 */
final class ConcurrencyLimitedChannel implements LimitedChannel {
    private static final Object CONTEXT = null;

    private final Channel delegate;
    private final Limiter<Object> limiter;

    @VisibleForTesting
    ConcurrencyLimitedChannel(Channel delegate, Limiter<Object> limiter) {
        this.delegate = delegate;
        this.limiter = limiter;
    }

    static ConcurrencyLimitedChannel create(Channel delegate) {
        AIMDLimit aimdLimit = AIMDLimit.newBuilder()
                // Don't count slow calls as a sign of the server being overloaded
                .timeout(Long.MAX_VALUE, TimeUnit.DAYS)
                .build();
        Limiter<Object> limiter = SimpleLimiter.newBuilder()
                .limit(WindowedLimit.newBuilder().build(aimdLimit))
                .build();
        return new ConcurrencyLimitedChannel(delegate, limiter);
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeCreateCall(Endpoint endpoint, Request request) {
        return limiter.acquire(CONTEXT).map(listener -> {
            ListenableFuture<Response> call = delegate.createCall(endpoint, request);
            Futures.addCallback(call, new LimiterCallback(listener), MoreExecutors.directExecutor());
            return call;
        });
    }

    /**
     * Signals back to the {@link Limiter} whether or not the request was successfully handled.
     */
    private static final class LimiterCallback implements FutureCallback<Response> {
        private static final ImmutableSet<Integer> DROP_CODES = ImmutableSet.of(429, 503);

        private final Limiter.Listener listener;

        private LimiterCallback(Limiter.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void onSuccess(Response result) {
            if (DROP_CODES.contains(result.code())) {
                listener.onDropped();
            } else {
                listener.onSuccess();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            listener.onIgnore();
        }
    }
}
