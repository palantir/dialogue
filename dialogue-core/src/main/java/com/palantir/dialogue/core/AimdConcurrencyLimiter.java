/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.FutureCallback;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntBinaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple lock-free additive increase multiplicative decrease concurrency limiter. Typically, a dispatching
 * {@link com.palantir.dialogue.Request} tries to {@link #acquire} a new permit and releases it when the
 * corresponding {@link Response} is retrieved.
 *
 * This class is a stripped-down version of the
 * <a href="https://github.com/Netflix/concurrency-limits">Netflix AIMD library</a>.
 */
final class AimdConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(AimdConcurrencyLimiter.class);
    private static final int INITIAL_LIMIT = 20;
    private static final double BACKOFF_RATIO = .9D;
    private static final int MIN_LIMIT = 1;
    // Effectively unlimited, reduced from MAX_VALUE to prevent overflow
    private static final int MAX_LIMIT = Integer.MAX_VALUE / 2;

    private final AtomicInteger limit = new AtomicInteger(INITIAL_LIMIT);
    private final AtomicInteger inFlight = new AtomicInteger();

    private final Ticker ticker;
    /** When should we apply the next limit increase. */
    private final AtomicLong nextIncreaseNanos;

    private final AtomicBoolean shouldIncreaseLimit = new AtomicBoolean(false);

    AimdConcurrencyLimiter(Ticker ticker) {
        this.ticker = ticker;
        this.nextIncreaseNanos =
                new AtomicLong(ticker.read() + Duration.ofSeconds(1).toNanos());
    }

    /**
     * Returns a new request permit if the number of {@link #getInflight in-flight} permits is smaller than the
     * current {@link #getLimit upper limit} of allowed concurrent permits. The caller is responsible for
     * eventually releasing the permit by calling exactly one of the {@link Permit#ignore}, {@link Permit#dropped},
     * or {@link Permit#success} methods.
     *
     * If the permit
     * is used in the context of a {@link Response Future&lt;Response&gt;} object, then passing the {@link Permit} as a
     * {@link FutureCallback callback} to the future will invoke either {@link Permit#onSuccess} or
     * {@link Permit#onFailure} which delegate to
     * ignore/dropped/success depending on the success or failure state of the response.
     * */
    Optional<Permit> acquire() {
        int currentInFlight = getInflight();
        if (currentInFlight >= getLimit()) {
            return Optional.empty();
        }
        return Optional.of(createPermit());
    }

    private Permit createPermit() {
        int inFlightSnapshot = inFlight.incrementAndGet();
        return new Permit(inFlightSnapshot);
    }

    final class Permit implements FutureCallback<Response> {
        private final int inFlightSnapshot;

        Permit(int inFlightSnapshot) {
            this.inFlightSnapshot = inFlightSnapshot;
        }

        @Override
        public void onSuccess(Response result) {
            if (Responses.isQosStatus(result) || Responses.isServerError(result)) {
                dropped();
            } else {
                success();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            if (throwable instanceof IOException) {
                dropped();
            } else {
                ignore();
            }
        }

        /**
         * Indicates that the effect of the request corresponding to this permit on concurrency limits should be
         * ignored.
         */
        void ignore() {
            inFlight.decrementAndGet();
        }

        /**
         * Indicates that the request corresponding to this permit was dropped and that the concurrency limit should be
         * multiplicatively decreased.
         */
        void dropped() {
            inFlight.decrementAndGet();
            down(inFlightSnapshot);
        }

        /**
         * Indicates that the request corresponding to this permit was successful and that the concurrency limit should
         * be additively increased.
         */
        void success() {
            inFlight.decrementAndGet();
            up(inFlightSnapshot);
        }
    }

    private void down(int inFlightSnapshot) {
        int newLimit = limit.accumulateAndGet(inFlightSnapshot, LimitUpdater.DROPPED);
        shouldIncreaseLimit.set(false);
        log.info("DOWN {}", SafeArg.of("newLimit", newLimit));
    }

    private void up(int inFlightSnapshot) {
        long now = ticker.read();
        long nextUpdate = nextIncreaseNanos.get();
        if (now < nextUpdate) {
            // store the change for later the next update
            shouldIncreaseLimit.compareAndSet(false, true);
        } else {
            // TODO(dfox): should the getLimit method also possibly apply a stored increase?
            if (nextIncreaseNanos.compareAndSet(
                    nextUpdate, now + Duration.ofSeconds(1).toNanos())) {
                // we managed to advance the window, so we get to increase the limit
                if (shouldIncreaseLimit.getAndSet(false)) {
                    int newLimit = limit.accumulateAndGet(inFlightSnapshot, LimitUpdater.SUCCESS);
                    log.info("UP {}", SafeArg.of("newLimit", newLimit));
                }
            }
        }
    }

    enum LimitUpdater implements IntBinaryOperator {
        SUCCESS() {
            @Override
            public int applyAsInt(int originalLimit, int inFlightSnapshot) {
                if (inFlightSnapshot * 2 >= originalLimit) {
                    return Math.min(MAX_LIMIT, originalLimit + 1);
                }
                return originalLimit;
            }
        },
        DROPPED() {
            @Override
            public int applyAsInt(int originalLimit, int _inFlightSnapshot) {
                return Math.max(MIN_LIMIT, (int) (originalLimit * BACKOFF_RATIO));
            }
        };
    }

    /**
     * Returns the current concurrency limit, i.e., the maximum number of concurrent {@link #getInflight in-flight}
     * permits such that another permit can be {@link #acquire acquired}.
     */
    int getLimit() {
        return limit.get();
    }

    /**
     * Returns the current number of in-flight permits, i.e., permits that been acquired but not yet released through
     * either of ignore/dropped/success.
     */
    int getInflight() {
        return inFlight.get();
    }

    @Override
    public String toString() {
        return "AimdConcurrencyLimiter{limit=" + limit + ", inFlight=" + inFlight + '}';
    }
}
