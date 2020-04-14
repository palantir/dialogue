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

import com.google.common.util.concurrent.FutureCallback;
import com.palantir.dialogue.Response;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;

/** Simple lock-free additive increase multiplicative decrease concurrency limiter. */
final class AimdConcurrencyLimiter {

    private static final int INITIAL_LIMIT = 20;
    private static final double BACKOFF_RATIO = .9D;
    private static final int MIN_LIMIT = 1;
    // Effectively unlimited, reduced from MAX_VALUE to prevent overflow
    private static final int MAX_LIMIT = Integer.MAX_VALUE / 2;

    private final AtomicInteger limit = new AtomicInteger(INITIAL_LIMIT);
    private final AtomicInteger inFlight = new AtomicInteger();

    Optional<Listener> acquire() {
        int currentInFlight = getInflight();
        if (currentInFlight >= getLimit()) {
            return Optional.empty();
        }
        return Optional.of(createListener());
    }

    private Listener createListener() {
        int inFlightSnapshot = inFlight.incrementAndGet();
        return new Listener(inFlightSnapshot);
    }

    final class Listener implements FutureCallback<Response> {
        private final int inFlightSnapshot;

        Listener(int inFlightSnapshot) {
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
        public void onFailure(Throwable _throwable) {
            ignore();
        }

        void ignore() {
            inFlight.decrementAndGet();
        }

        void dropped() {
            inFlight.decrementAndGet();
            limit.accumulateAndGet(inFlightSnapshot, LimitUpdater.DROPPED);
        }

        void success() {
            inFlight.decrementAndGet();
            limit.accumulateAndGet(inFlightSnapshot, LimitUpdater.SUCCESS);
        }
    }

    enum LimitUpdater implements IntBinaryOperator {
        SUCCESS() {
            @Override
            public int applyAsInt(int originalLimit, int inFlightSnapshot) {
                if (inFlightSnapshot * 2 >= originalLimit) {
                    int updatedLimit = originalLimit + 1;
                    if (updatedLimit >= MAX_LIMIT) {
                        updatedLimit = Math.max(MIN_LIMIT, updatedLimit / 2);
                    }
                    return Math.min(MAX_LIMIT, updatedLimit);
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

    int getLimit() {
        return limit.get();
    }

    int getInflight() {
        return inFlight.get();
    }

    @Override
    public String toString() {
        return "AimdConcurrencyLimiter{limit=" + limit + ", inFlight=" + inFlight + '}';
    }
}
