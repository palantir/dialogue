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
 * Simple lock-free concurrency limiter. Typically, a dispatching
 * {@link com.palantir.dialogue.Request} tries to {@link #acquire} a new permit and releases it when the
 * corresponding {@link Response} is retrieved.
 * This limiter uses an algorithm similar to additive increase multiplicative decrease (AIMD) with
 * greater restriction on the increase component, requiring more successful requests to increase the limit
 * as the limit grows.
 *
 * This class loosely based on the
 * <a href="https://github.com/Netflix/concurrency-limits">Netflix AIMD library</a>.
 */
final class CautiousIncreaseAggressiveDecreaseConcurrencyLimiter {

    private static final Logger log =
            LoggerFactory.getLogger(CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.class);
    private static final double INITIAL_LIMIT = 20;
    private static final double BACKOFF_RATIO = .9D;
    private static final double MIN_LIMIT = 1;
    private static final double MAX_LIMIT = 1_000_000D;

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

        // We don't want to hand out a permit if there are 4 inflight and a limit of 4.1, as this will immediately send
        // our inflight number to 5, which is clearly above the limit.  Instead, we wait until there is capacity for
        // one whole request before handing out a permit. In the worst-case scenario with zero inflight and a limit of
        // 1, we'll still hand out a permit
        if (currentInFlight <= getLimit() - 1) {
            int inFlightSnapshot = inFlight.incrementAndGet();
            return Optional.of(new Permit(inFlightSnapshot));
        }
        return Optional.empty();
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
            double newLimit = accumulateAndGetLimit(inFlightSnapshot, LimitUpdater.DROPPED);
            if (log.isDebugEnabled()) {
                log.debug("Decreasing limit {}", SafeArg.of("newLimit", newLimit));
            }
        }

        /**
         * Indicates that the request corresponding to this permit was successful and that the concurrency limit should
         * be additively increased.
         */
        void success() {
            inFlight.decrementAndGet();
            double newLimit = accumulateAndGetLimit(inFlightSnapshot, LimitUpdater.SUCCESS);
            if (log.isDebugEnabled()) {
                log.debug("Increasing limit {}", SafeArg.of("newLimit", newLimit));
            }
        }
    }

    enum LimitUpdater implements DoubleBinaryOperator {
        SUCCESS() {
            @Override
            public double applyAsDouble(double originalLimit, double inFlightSnapshot) {
                if (inFlightSnapshot >= Math.floor(originalLimit * BACKOFF_RATIO)) {
                    // The limit is raised more easily when the maximum limit is low, and becomes linearly more
                    // stubborn as the limit increases. Given a fixed rate of traffic this should result in
                    // linear slope as opposed to the exponential slope expected from a static increment
                    // value.
                    double increment = 1D / originalLimit;
                    return Math.min(MAX_LIMIT, originalLimit + increment);
                }
                return originalLimit;
            }
        },
        DROPPED() {
            @Override
            public double applyAsDouble(double originalLimit, double _inFlightSnapshot) {
                // Floor the new value to avoid effectively no-op decreases when the limit
                // close to 1.
                return Math.max(MIN_LIMIT, Math.floor(originalLimit * BACKOFF_RATIO));
            }
        };
    }

    private double accumulateAndGetLimit(int value, DoubleBinaryOperator accumulatorFunction) {
        // Capture the limit field reference once to avoid work in a tight loop. The JIT cannot
        // reliably optimize out references to final fields due to the potential for reflective
        // modification.
        AtomicDouble localLimit = limit;
        while (true) {
            double limitSnapshot = localLimit.get();
            double accumulatorResult = accumulatorFunction.applyAsDouble(limitSnapshot, value);
            if (localLimit.compareAndSet(limitSnapshot, accumulatorResult)) {
                return accumulatorResult;
            }
        }
    }

    /**
     * Returns the current concurrency limit, i.e., the maximum number of concurrent {@link #getInflight in-flight}
     * permits such that another permit can be {@link #acquire acquired}.
     */
    double getLimit() {
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
