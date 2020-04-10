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

import com.netflix.concurrency.limits.Limiter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;
import javax.annotation.Nullable;

final class ConjureLimiter implements Limiter<Void> {

    private static final int INITIAL_LIMIT = 20;
    private static final double BACKOFF_RATIO = .9D;
    private static final int MIN_LIMIT = 1;
    // Effectively unlimited, reduced from MAX_VALUE to prevent overflow
    private static final int MAX_LIMIT = Integer.MAX_VALUE / 2;

    private static final IntBinaryOperator limitUpdaterDidDrop = newLimitUpdater(true);
    private static final IntBinaryOperator limitUpdaterDidNotDrop = newLimitUpdater(false);

    private final AtomicInteger limit = new AtomicInteger(INITIAL_LIMIT);
    private final AtomicInteger inFlight = new AtomicInteger();

    protected Listener createListener() {
        final int currentInflight = inFlight.incrementAndGet();
        return new Listener() {
            @Override
            public void onSuccess() {
                inFlight.decrementAndGet();
                onSample(currentInflight, false);
            }

            @Override
            public void onIgnore() {
                inFlight.decrementAndGet();
            }

            @Override
            public void onDropped() {
                inFlight.decrementAndGet();
                onSample(currentInflight, true);
            }
        };
    }

    @Override
    public Optional<Listener> acquire(@Nullable Void _context) {
        int currentInFlight = getInflight();
        if (currentInFlight >= getLimit()) {
            return Optional.empty();
        }
        return Optional.of(createListener());
    }

    void onSample(int inFlightSnapshot, boolean didDrop) {
        limit.accumulateAndGet(inFlightSnapshot, didDrop ? limitUpdaterDidDrop : limitUpdaterDidNotDrop);
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
