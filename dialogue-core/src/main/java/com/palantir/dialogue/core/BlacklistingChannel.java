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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
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
    private final AtomicReference<BlacklistState> channelBlacklistState;

    BlacklistingChannel(LimitedChannel delegate, Duration duration) {
        this(delegate, duration, Ticker.systemTicker());
    }

    @VisibleForTesting
    BlacklistingChannel(LimitedChannel delegate, Duration duration, Ticker ticker) {
        this.delegate = delegate;
        this.duration = duration;
        this.ticker = ticker;
        this.channelBlacklistState = new AtomicReference<>();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        BlacklistState state = channelBlacklistState.get();
        if (state != null) {
            return state.maybeExecute(endpoint, request);
        }
        return delegate.maybeExecute(endpoint, request)
                .map(future -> DialogueFutures.addDirectCallback(future, new BlacklistingCallback(null)));
    }

    @Override
    public String toString() {
        return "BlacklistingChannel{" + delegate + '}';
    }

    private final class BlacklistState {
        private final AtomicReference<Probation> probation = new AtomicReference<>();
        private final long blacklistUntilNanos;
        private final int probationPermits;

        BlacklistState(Duration duration, int probationPermits) {
            this.blacklistUntilNanos = ticker.read() + duration.toNanos();
            this.probationPermits = probationPermits;
        }

        Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
            Optional<Probation> maybeProbation = maybeBeginProbation();
            if (!maybeProbation.isPresent()) {
                return Optional.empty();
            }
            if (maybeProbation.get().acquireStartPermit()) {
                log.debug("Probation channel request allowed");
                return delegate.maybeExecute(endpoint, request)
                        .map(future -> DialogueFutures.addDirectCallback(future, new BlacklistingCallback(this)));
            } else {
                log.debug("Probation channel request not allowed");
                return Optional.empty();
            }
        }

        Optional<Probation> maybeBeginProbation() {
            Optional<Probation> maybeProbation = Optional.ofNullable(probation.get());
            if (maybeProbation.isPresent()) {
                return maybeProbation;
            }
            if (ticker.read() >= blacklistUntilNanos) {
                if (probation.compareAndSet(null, new Probation(probationPermits))) {
                    if (log.isDebugEnabled()) {
                        log.debug("Channel {} is entering probation", UnsafeArg.of("channel", delegate));
                    }
                }
                return Optional.ofNullable(probation.get());
            }
            return Optional.empty();
        }

        void markSuccess() {
            Probation maybeProbation = probation.get();
            if (maybeProbation != null && maybeProbation.checkIfProbationIsComplete()) {
                log.debug("Clearing probation state");
                if (channelBlacklistState.compareAndSet(this, null)) {
                    log.debug("Channel is no longer blacklisted");
                } else {
                    log.debug("Blacklist state has already been updated");
                }
            }
        }
    }

    private static final class Probation {
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

    private final class BlacklistingCallback implements FutureCallback<Response> {

        @Nullable
        private final BlacklistState initialState;

        BlacklistingCallback(@Nullable BlacklistState initialState) {
            this.initialState = initialState;
        }

        @Override
        public void onSuccess(Response response) {
            // TODO(jellis): use the Retry-After header (if present) to determine how long to blacklist the channel
            if (Responses.isQosStatus(response) || Responses.isServerError(response)) {
                log.debug(
                        "Blacklisting {} due to status code {}",
                        UnsafeArg.of("delegate", delegate),
                        SafeArg.of("code", response.code()));
                if (!channelBlacklistState.compareAndSet(
                        initialState, new BlacklistState(duration, NUM_PROBATION_REQUESTS))) {
                    log.debug("blacklist state has not been updated because it has changed since this request was"
                            + " created");
                }
            } else {
                BlacklistState state = channelBlacklistState.get();
                if (state != null) {
                    state.markSuccess();
                }
            }
        }

        @Override
        public void onFailure(Throwable _throwable) {
            if (!channelBlacklistState.compareAndSet(
                    initialState, new BlacklistState(duration, NUM_PROBATION_REQUESTS))) {
                log.debug("blacklist state has not been updated because it has changed since this request was created");
            }
        }
    }
}
