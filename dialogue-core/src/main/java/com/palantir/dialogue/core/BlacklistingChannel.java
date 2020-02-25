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
import com.google.common.util.concurrent.Futures;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
    public ListenableFuture<LimitedResponse> maybeExecute(Endpoint endpoint, Request request) {
        BlacklistState state = channelBlacklistState.get();
        if (state != null) {
            return state.maybeExecute(endpoint, request);
        }
        return DialogueFutures.addDirectCallback(
                delegate.maybeExecute(endpoint, request), new BlacklistingCallback(null));
    }

    @Override
    public String toString() {
        return "BlacklistingChannel{" + delegate + '}';
    }

    private final class BlacklistState implements LimitedChannel {
        private final AtomicReference<Probation> probation = new AtomicReference<>();
        private final ScheduledFuture<?> scheduledFuture;
        private final long blacklistUntilNanos;
        private final int probationPermits;

        BlacklistState(Duration duration, int probationPermits) {
            this.blacklistUntilNanos = ticker.read() + duration.toNanos();
            this.probationPermits = probationPermits;
            this.scheduledFuture =
                    scheduler.schedule(this::maybeBeginProbation, duration.toNanos(), TimeUnit.NANOSECONDS);
        }

        @Override
        public ListenableFuture<LimitedResponse> maybeExecute(Endpoint endpoint, Request request) {
            Optional<Probation> maybeProbation = maybeBeginProbation();
            if (!maybeProbation.isPresent()) {
                return Futures.immediateFuture(LimitedResponses.clientLimited());
            }
            if (maybeProbation.get().acquireStartPermit()) {
                log.debug("Probation channel request allowed");
                return DialogueFutures.addDirectCallback(
                        delegate.maybeExecute(endpoint, request), new BlacklistingCallback(this));
            } else {
                log.debug("Probation channel request not allowed");
                return Futures.immediateFuture(LimitedResponses.clientLimited());
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
                    listener.onChannelReady();
                    scheduledFuture.cancel(false);
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
                    listener.onChannelReady();
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

    private final class BlacklistingCallback implements FutureCallback<LimitedResponse> {

        @Nullable
        private final BlacklistState initialState;

        BlacklistingCallback(@Nullable BlacklistState initialState) {
            this.initialState = initialState;
        }

        @Override
        public void onSuccess(LimitedResponse response) {
            response.matches(new LimitedResponse.Cases<Void>() {
                // TODO(jellis): use the Retry-After header (if present) to determine how long to blacklist the channel
                @Override
                public Void serverLimited(Response response) {
                    handleFailure(response);
                    return null;
                }

                @Override
                public Void clientLimited() {
                    // No-op if the client limited the request
                    return null;
                }

                @Override
                public Void serverError(Response response) {
                    handleFailure(response);
                    return null;
                }

                @Override
                public Void success(Response _response) {
                    BlacklistState state = channelBlacklistState.get();
                    if (state != null) {
                        state.markSuccess();
                    }
                    return null;
                }

                private void handleFailure(Response response) {
                    log.debug(
                            "Blacklisting {} due to status code {}",
                            UnsafeArg.of("delegate", delegate),
                            SafeArg.of("code", response.code()));
                    if (!channelBlacklistState.compareAndSet(
                            initialState, new BlacklistState(duration, NUM_PROBATION_REQUESTS))) {
                        log.debug("blacklist state has not been updated because it has changed since this request was"
                                + " created");
                    }
                }
            });
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
