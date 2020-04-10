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

final class ConjureLimiter {

    private static final int INITIAL_LIMIT = 20;
    private static final double BACKOFF_RATIO = .9D;
    private static final int MIN_LIMIT = 1;
    // Effectively unlimited, reduced from MAX_VALUE to prevent overflow
    private static final int MAX_LIMIT = Integer.MAX_VALUE / 2;

    private static final IntBinaryOperator limitUpdaterDidDrop = newLimitUpdater(true);
    private static final IntBinaryOperator limitUpdaterSuccess = newLimitUpdater(false);

    private final AtomicInteger limit = new AtomicInteger(INITIAL_LIMIT);
    private final AtomicInteger inFlight = new AtomicInteger();

    public Optional<Listener> acquire() {
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
            limit.accumulateAndGet(inFlightSnapshot, limitUpdaterDidDrop);
        }

        void success() {
            inFlight.decrementAndGet();
            limit.accumulateAndGet(inFlightSnapshot, limitUpdaterSuccess);
        }
    }

    private static IntBinaryOperator newLimitUpdater(boolean didDrop) {
        return (currentLimit, inFlightSnapshot) -> {
            if (didDrop) {
                currentLimit = (int) (currentLimit * BACKOFF_RATIO);
            } else if (inFlightSnapshot * 2 >= currentLimit) {
                currentLimit = currentLimit + 1;
            }
            if (currentLimit >= MAX_LIMIT) {
                currentLimit = currentLimit / 2;
            }
            return Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, currentLimit));
        };
    }

    int getLimit() {
        return limit.get();
    }

    int getInflight() {
        return inFlight.get();
    }
}
