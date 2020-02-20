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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A channel that monitors the successes and failures of requests in order to determine the number of concurrent
 * requests allowed to a particular channel. If the channel's concurrency limit has been reached, the
 * {@link #maybeExecute} method returns empty.
 */
final class ConcurrencyLimitedChannel implements LimitedChannel {
    private static final Void NO_CONTEXT = null;
    private static final Ticker SYSTEM_NANOTIME = System::nanoTime;

    private final LimitedChannel delegate;
    private final LoadingCache<Endpoint, Limiter<Void>> limiters;

    @VisibleForTesting
    ConcurrencyLimitedChannel(LimitedChannel delegate, Supplier<Limiter<Void>> limiterSupplier) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.limiters =
                Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).build(key -> limiterSupplier.get());
    }

    static ConcurrencyLimitedChannel create(LimitedChannel delegate) {
        return new ConcurrencyLimitedChannel(delegate, () -> ConcurrencyLimitedChannel.createLimiter(SYSTEM_NANOTIME));
    }

    @VisibleForTesting
    static Limiter<Void> createLimiter(Ticker nanoTimeClock) {
        AIMDLimit aimdLimit = AIMDLimit.newBuilder()
                // Explicitly set values to prevent library changes from breaking us
                .initialLimit(20)
                .minLimit(1)
                .maxLimit(200)
                .backoffRatio(0.9)
                // Don't count slow calls as a sign of the server being overloaded
                .timeout(Long.MAX_VALUE, TimeUnit.DAYS)
                .build();
        return SimpleLimiter.newBuilder()
                .clock(nanoTimeClock::read)
                .limit(WindowedLimit.newBuilder().build(aimdLimit))
                .build();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        return limiters.get(endpoint).acquire(NO_CONTEXT).flatMap(listener -> {
            Optional<ListenableFuture<Response>> result = delegate.maybeExecute(endpoint, request);
            if (result.isPresent()) {
                DialogueFutures.addDirectCallback(result.get(), new LimiterCallback(listener));
            } else {
                listener.onIgnore();
            }
            return result;
        });
    }

    /**
     * Signals back to the {@link Limiter} whether or not the request was successfully handled.
     */
    private static final class LimiterCallback implements FutureCallback<Response> {
        private static final ImmutableSet<Integer> DROP_CODES = ImmutableSet.of(429);

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
        public void onFailure(Throwable _throwable) {
            listener.onIgnore();
        }
    }
}
