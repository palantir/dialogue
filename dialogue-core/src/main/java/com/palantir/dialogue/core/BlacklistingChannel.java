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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delegates execution to the given {@link LimitedChannel} until a failure is seen, at which point the channel will
 * return {@link Optional#empty()} until the given {@link Duration} has elapsed.
 *
 * When the blacklisting {@link Duration} has elapsed, the channel enters a 'probation' period, where a few requests
 * are allowed to be sent off - all of which must be successful for probation to be passed and the channel fully
 * unblacklisted. Without this functionality, hundreds of requests could be sent to a still-broken
 * server before the first of them returns and tells us it's still broken.
 */
final class BlacklistingChannel implements LimitedChannel {

    private static final Logger log = LoggerFactory.getLogger(BlacklistingChannel.class);
    private static final int NUM_PROBATION_REQUESTS = 5;

    private final LimitedChannel delegate;
    private final Duration duration;
    private final Ticker ticker;

    private volatile BlacklistState state = NoRestrictions.INSTANCE;

    BlacklistingChannel(LimitedChannel delegate, Duration duration) {
        this(delegate, duration, Ticker.systemTicker());
    }

    @VisibleForTesting
    BlacklistingChannel(LimitedChannel delegate, Duration duration, Ticker ticker) {
        this.delegate = delegate;
        this.duration = duration;
        this.ticker = ticker;
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        if (state instanceof BlacklistUntil && stillBlacklisted((BlacklistUntil) state)) {
            return Optional.empty();
        }

        if (state instanceof Probation) {
            Probation probation = (Probation) state;
            if (probation.acquireStartPermit()) {
                log.debug("Probation channel request allowed");
                return delegate.maybeExecute(endpoint, request).map(future ->
                        DialogueFutures.addDirectCallback(future, new BlacklistingCallback(Optional.of(probation))));
            } else {
                log.debug("Probation channel request not allowed");
                return Optional.empty();
            }
        }

        return delegate.maybeExecute(endpoint, request)
                .map(future -> DialogueFutures.addDirectCallback(future, new BlacklistingCallback(Optional.empty())));
    }

    private void blacklist() {
        state = new BlacklistUntil(ticker.read() + duration.toNanos());
    }

    private boolean stillBlacklisted(BlacklistUntil blacklistUntil) {
        if (ticker.read() < blacklistUntil.untilNanos) {
            return true;
        }

        state = new Probation(NUM_PROBATION_REQUESTS);
        return false;
    }

    private final class BlacklistingCallback implements FutureCallback<Response> {
        private final Optional<Probation> probationPermit;

        private BlacklistingCallback(Optional<Probation> probationPermit) {
            this.probationPermit = probationPermit;
        }

        @Override
        public void onSuccess(Response response) {
            // TODO(jellis): use the Retry-After header (if present) to determine how long to blacklist the channel
            if (response.code() == 503 || response.code() == 500) {
                blacklist();
            } else if (probationPermit.isPresent() && probationPermit.get().checkIfProbationIsComplete()) {
                log.debug("Probation is complete");
                state = NoRestrictions.INSTANCE;
            }
        }

        @Override
        public void onFailure(Throwable _throwable) {
            blacklist();
        }
    }

    @Override
    public String toString() {
        return "BlacklistingChannel{delegate=" + delegate + '}';
    }

    // I wish java had union types
    interface BlacklistState {}

    private enum NoRestrictions implements BlacklistState {
        INSTANCE
    }

    private static final class BlacklistUntil implements BlacklistState {
        private final long untilNanos;

        private BlacklistUntil(long untilNanos) {
            this.untilNanos = untilNanos;
        }
    }

    private static final class Probation implements BlacklistState {
        private final AtomicInteger startPermits;
        private final AtomicInteger successesRequired;

        Probation(int number) {
            startPermits = new AtomicInteger(number);
            successesRequired = new AtomicInteger(number);
        }

        boolean acquireStartPermit() {
            int newStarts = startPermits.decrementAndGet();
            return newStarts >= 0;
        }

        boolean checkIfProbationIsComplete() {
            int remaining = successesRequired.decrementAndGet();
            return remaining <= 0;
        }
    }
}
