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

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.common.util.concurrent.FutureCallback;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleBinaryOperator;
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
    private static final double INITIAL_LIMIT = 20;
    private static final double BACKOFF_RATIO = .9D;
    private static final double MIN_LIMIT = 1;
    // Effectively unlimited, reduced from MAX_VALUE to prevent overflow
    private static final int MAX_LIMIT = Integer.MAX_VALUE / 2;

    private final AtomicDouble limit = new AtomicDouble(INITIAL_LIMIT);
    private final AtomicInteger inFlight = new AtomicInteger();

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
        return Optional.of(createToken());
    }

    private Permit createToken() {
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
            double newLimit = accumulateAndGet(limit, inFlightSnapshot, LimitUpdater.DROPPED);
            log.info("DOWN {}", SafeArg.of("newLimit", newLimit));
        }

        /**
         * Indicates that the request corresponding to this permit was successful and that the concurrency limit should
         * be additively increased.
         */
        void success() {
            inFlight.decrementAndGet();
            double newLimit = accumulateAndGet(limit, inFlightSnapshot, LimitUpdater.SUCCESS);
            log.info("UP {}", SafeArg.of("newLimit", newLimit));
        }
    }

    private static double accumulateAndGet(
            AtomicDouble atomicDouble, double argument, DoubleBinaryOperator accumulatorFunction) {
        double prev = atomicDouble.get();
        double next = 0;
        for (boolean haveNext = false; ; ) {
            if (!haveNext) {
                next = accumulatorFunction.applyAsDouble(prev, argument);
            }
            if (atomicDouble.compareAndSet(prev, next)) {
                return next;
            }
            haveNext = (prev == (prev = atomicDouble.get()));
        }
    }

    enum LimitUpdater implements DoubleBinaryOperator {
        SUCCESS() {
            @Override
            public double applyAsDouble(double originalLimit, double inFlightSnapshot) {
                if (inFlightSnapshot >= originalLimit * BACKOFF_RATIO) {
                    return Math.min(MAX_LIMIT, originalLimit + (1D / originalLimit));
                }
                return originalLimit;
            }
        },
        DROPPED() {
            @Override
            public double applyAsDouble(double originalLimit, double _inFlightSnapshot) {
                return Math.max(MIN_LIMIT, originalLimit * BACKOFF_RATIO);
            }
        };
    }

    /**
     * Returns the current concurrency limit, i.e., the maximum number of concurrent {@link #getInflight in-flight}
     * permits such that another permit can be {@link #acquire acquired}.
     */
    int getLimit() {
        return Ints.saturatedCast(Math.round(limit.get()));
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
