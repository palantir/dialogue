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
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
    /*
     * Shared single thread executor is reused between all blacklisting channels. If it becomes oversaturated
     * we may wait longer than expected before resuming requests to blacklisted channels, but this is an
     * edge case where things are already operating in a degraded state.
     */
    private static final Supplier<ScheduledExecutorService> sharedScheduler = Suppliers.memoize(
            () -> Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                    .setNameFormat("dialogue-BlacklistingChannel-scheduler-%d")
                    .setDaemon(false)
                    .build()));

    private final LimitedChannel delegate;
    private final Duration duration;
    private final Ticker ticker;
    private final LimitedChannelListener listener;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<BlacklistState> channelBlacklistState;

    BlacklistingChannel(LimitedChannel delegate, Duration duration, LimitedChannelListener listener) {
        this(delegate, duration, listener, Ticker.systemTicker(), sharedScheduler.get());
    }

    @VisibleForTesting
    BlacklistingChannel(LimitedChannel delegate, Duration duration, LimitedChannelListener listener, Ticker ticker) {
        this(delegate, duration, listener, ticker, sharedScheduler.get());
    }

    @VisibleForTesting
    BlacklistingChannel(
            LimitedChannel delegate,
            Duration duration,
            LimitedChannelListener listener,
            Ticker ticker,
            ScheduledExecutorService scheduler) {
        this.delegate = delegate;
        this.duration = duration;
        this.ticker = ticker;
        this.listener = listener;
        this.scheduler = scheduler;
        this.channelBlacklistState = new AtomicReference<>();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        BlacklistState state = channelBlacklistState.get();
        if (state != null) {
            BlacklistStage stage = state.maybeProgressAndGet();
            if (stage instanceof BlacklistUntil) {
                return Optional.empty();
            }

            if (stage instanceof Probation) {
                Probation probation = (Probation) stage;
                if (probation.acquireStartPermit()) {
                    log.debug("Probation channel request allowed");
                    return delegate.maybeExecute(endpoint, request).map(future -> DialogueFutures.addDirectCallback(
                            future, new BlacklistingCallback(Optional.of(probation))));
                } else {
                    log.debug("Probation channel request not allowed");
                    return Optional.empty();
                }
            }
        }

        return delegate.maybeExecute(endpoint, request)
                .map(future -> DialogueFutures.addDirectCallback(future, new BlacklistingCallback(Optional.empty())));
    }

    private void blacklist() {
        BlacklistState state = new BlacklistState(duration, NUM_PROBATION_REQUESTS);
        channelBlacklistState.set(state);
    }

    private void probationComplete() {
        channelBlacklistState.set(null);
        listener.onChannelReady();
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

    private final class BlacklistState {
        private final AtomicBoolean inProbation = new AtomicBoolean();
        private final BlacklistUntil blacklistUntil;
        private final Probation probation;
        private final ScheduledFuture<?> future;

        BlacklistState(Duration duration, int probationPermits) {
            this.blacklistUntil = new BlacklistUntil(ticker.read() + duration.toNanos());
            this.probation = new Probation(probationPermits);
            this.future = scheduler.schedule(this::maybeProgressAndGet, duration.toNanos(), TimeUnit.NANOSECONDS);
        }

        BlacklistStage maybeProgressAndGet() {
            if (inProbation.get()) {
                return probation;
            }
            if (ticker.read() >= blacklistUntil.untilNanos) {
                if (inProbation.compareAndSet(false, true)) {
                    listener.onChannelReady();
                    future.cancel(false);
                }
                return probation;
            }
            return blacklistUntil;
        }
    }

    // I wish java had union types
    interface BlacklistStage {}

    private static final class BlacklistUntil implements BlacklistStage {
        private final long untilNanos;

        private BlacklistUntil(long untilNanos) {
            this.untilNanos = untilNanos;
        }
    }

    private static final class Probation implements BlacklistStage {
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
