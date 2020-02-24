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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
final class BlacklistingChannel implements CompositeLimitedChannel {

    private static final Logger log = LoggerFactory.getLogger(BlacklistingChannel.class);
    private static final int NUM_PROBATION_REQUESTS = 5;

    private final CompositeLimitedChannel delegate;
    private final Duration duration;
    private final Ticker ticker;
    private final AtomicReference<BlacklistState> channelBlacklistState;

    BlacklistingChannel(CompositeLimitedChannel delegate, Duration duration) {
        this(delegate, duration, Ticker.systemTicker());
    }

    @VisibleForTesting
    BlacklistingChannel(CompositeLimitedChannel delegate, Duration duration, Ticker ticker) {
        this.delegate = delegate;
        this.duration = duration;
        this.ticker = ticker;
        this.channelBlacklistState = new AtomicReference<>();
    }

    @Override
    public LimitedResponse maybeExecute(Endpoint endpoint, Request request) {
        BlacklistState state = channelBlacklistState.get();

        if (state instanceof BlacklistUntil) {
            if (ticker.read() < ((BlacklistUntil) state).untilNanos) {
                return LimitedResponses.blacklisted();
            }

            state = beginProbation();
        }

        if (state instanceof Probation) {
            Probation probation = (Probation) state;
            if (probation.acquireStartPermit()) {
                log.debug("Probation channel request allowed");
                return wrap(delegate.maybeExecute(endpoint, request), Optional.of(probation));
            } else {
                log.debug("Probation channel request not allowed");
                return LimitedResponses.blacklisted();
            }
        }

        return wrap(delegate.maybeExecute(endpoint, request), Optional.empty());
    }

    private void blacklist() {
        channelBlacklistState.set(new BlacklistUntil(ticker.read() + duration.toNanos()));
    }

    private Probation beginProbation() {
        Probation probation = new Probation(NUM_PROBATION_REQUESTS);
        channelBlacklistState.set(probation);
        return probation;
    }

    private void probationComplete() {
        channelBlacklistState.set(null);
    }

    private LimitedResponse wrap(LimitedResponse limitedResponse, Optional<Probation> probation) {
        LimitedResponses.getResponse(limitedResponse).ifPresent(response -> {
            Futures.addCallback(response, new BlacklistingCallback(probation), MoreExecutors.directExecutor());
        });
        return limitedResponse;
    }

    private final class BlacklistingCallback implements FutureCallback<Response> {
        private final Optional<Probation> probationPermit;

        private BlacklistingCallback(Optional<Probation> probationPermit) {
            this.probationPermit = probationPermit;
        }

        @Override
        public void onSuccess(Response response) {
            // TODO(jellis): use the Retry-After header (if present) to determine how long to blacklist the channel
            if (Responses.isQosStatus(response) || Responses.isServerError(response)) {
                log.debug(
                        "Blacklisting {} due to status code {}",
                        UnsafeArg.of("delegate", delegate),
                        SafeArg.of("code", response.code()));
                blacklist();
            } else if (probationPermit.isPresent() && probationPermit.get().checkIfProbationIsComplete()) {
                log.debug("Probation is complete");
                probationComplete();
            }
        }

        @Override
        public void onFailure(Throwable _throwable) {
            blacklist();
        }
    }

    @Override
    public String toString() {
        return "BlacklistingChannel{" + delegate + '}';
    }

    // I wish java had union types
    interface BlacklistState {}

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
