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

import com.codahale.metrics.Meter;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * A channel that monitors the successes and failures of requests in order to determine the number of concurrent
 * requests allowed to a particular channel. If the channel's concurrency limit has been reached, the
 * {@link #maybeExecute} method returns empty.
 */
final class ConcurrencyLimitedChannel implements LimitedChannel {
    @Nullable
    private static final Void NO_CONTEXT = null;

    private static final Ticker SYSTEM_NANOTIME = System::nanoTime;

    private final Meter limitedMeter;
    private final LimitedChannel delegate;
    private final Limiter<Void> limiter;

    @VisibleForTesting
    ConcurrencyLimitedChannel(LimitedChannel delegate, Limiter<Void> limiter, DialogueClientMetrics metrics) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.limitedMeter = metrics.limited(getClass().getSimpleName());
        this.limiter = limiter;
    }

    static ConcurrencyLimitedChannel create(LimitedChannel delegate, DialogueClientMetrics metrics) {
        return new ConcurrencyLimitedChannel(
                delegate, ConcurrencyLimitedChannel.createLimiter(SYSTEM_NANOTIME), metrics);
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
        Optional<Limiter.Listener> maybeListener = limiter.acquire(NO_CONTEXT);
        if (maybeListener.isPresent()) {
            Limiter.Listener listener = maybeListener.get();
            Optional<ListenableFuture<Response>> result = delegate.maybeExecute(endpoint, request);
            if (result.isPresent()) {
                DialogueFutures.addDirectCallback(result.get(), new LimiterCallback(listener));
            } else {
                listener.onIgnore();
            }
            return result;
        } else {
            limitedMeter.mark();
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "ConcurrencyLimitedChannel{" + delegate + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        ConcurrencyLimitedChannel that = (ConcurrencyLimitedChannel) object;
        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    /**
     * Signals back to the {@link Limiter} whether or not the request was successfully handled.
     */
    private static final class LimiterCallback implements FutureCallback<Response> {

        private final Limiter.Listener listener;

        private LimiterCallback(Limiter.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void onSuccess(Response result) {
            if (Responses.isTooManyRequests(result)) {
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
