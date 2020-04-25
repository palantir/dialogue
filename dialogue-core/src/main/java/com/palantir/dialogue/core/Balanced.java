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

import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses nodes based on stats about each channel, i.e. how many requests are currently
 * being served and also how many failures have been seen in the last few seconds.
 *
 * This is intended to be a strict improvement over Round Robin and Random Selection which can leave fast servers
 * underutilized, as it sends the same number to both a slow and fast node. It is *not* appropriate for transactional
 * workloads (where n requests must all land on the same server) or scenarios where cache warming is very important.
 * {@link PinUntilErrorChannel} remains the best choice for these.
 */
final class Balanced implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(Balanced.class);

    /**
     * We'd like to remember failures for a long time, but this would increase CPU on a hot codepath as we compute
     * the number of failures for each channel in order to sort. See {@link ChannelWithStats#score}.
     */
    private static final Duration FAILURE_MEMORY = Duration.ofSeconds(5);

    /**
     * This comparator is a little risky because the data can change while we're sorting. In practise the sort
     * finishes a lot quicker than network requests do, and we don't mind if it's not exactly right.
     */
    @VisibleForTesting
    static final Comparator<ChannelWithStats> BY_SCORE = Comparator.comparing(ChannelWithStats::score);

    private final ImmutableList<ChannelWithStats> channels;
    private final Random random;
    private final CodahaleClock clock;

    Balanced(ImmutableList<LimitedChannel> channels, Random random, Ticker ticker) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.random = random;
        this.clock = new CodahaleClock(ticker);
        this.channels = channels.stream()
                .map(channel -> new ChannelWithStats(channel, clock))
                .collect(ImmutableList.toImmutableList());

        log.debug("Initialized", SafeArg.of("count", channels.size()), UnsafeArg.of("channels", channels));
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Channel scores {}",
                    SafeArg.of("scores", Lists.transform(channels, ChannelWithStats::score)),
                    SafeArg.of("inflight", Lists.transform(channels, ChannelWithStats::inflight)));
        }

        // pre-shuffling is pretty important here, otherwise when there are no requests in flight, we'd
        // *always* prefer the first channel of the list, leading to a higher overall load.
        List<ChannelWithStats> preShuffled = shuffleImmutableList(channels, random);

        // TODO(dfox): P2C optimization when we have high number of nodes to save CPU?
        // http://www.eecs.harvard.edu/~michaelm/NEWWORK/postscripts/twosurvey.pdf
        return preShuffled.stream()
                .sorted(BY_SCORE)
                .map(channel -> channel.maybeExecute(endpoint, request))
                .filter(Optional::isPresent)
                .map(Optional::get)
                // we rely heavily on streams being lazy here
                .findFirst();
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }

    static final class ChannelWithStats implements LimitedChannel {
        private final LimitedChannel delegate;

        @VisibleForTesting
        final AtomicInteger inflight = new AtomicInteger(0);

        /**
         * We keep track of failures within a time window to do well in scenaios where an unhealthy server returns
         * errors much faster than healthy nodes can serve good responses. See
         * {@link SimulationTest#fast_503s_then_revert}.
         */
        @VisibleForTesting
        final SlidingTimeWindowArrayReservoir recentFailures;

        // Saves one allocation on each network call
        private final FutureCallback<Response> updateStats = new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response result) {
                inflight.decrementAndGet();
                if (Responses.isQosStatus(result) || Responses.isServerError(result)) {
                    recentFailures.update(1);
                }
            }

            @Override
            public void onFailure(Throwable _throwable) {
                inflight.decrementAndGet();
                recentFailures.update(1);
            }
        };

        ChannelWithStats(LimitedChannel delegate, CodahaleClock clock) {
            this.delegate = delegate;
            this.recentFailures =
                    new SlidingTimeWindowArrayReservoir(FAILURE_MEMORY.toNanos(), TimeUnit.NANOSECONDS, clock);
        }

        /** Low = good. */
        int score() {
            return inflight.get() + recentFailures.size();
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
            inflight.incrementAndGet();
            Optional<ListenableFuture<Response>> maybe = delegate.maybeExecute(endpoint, request);
            if (maybe.isPresent()) {
                DialogueFutures.addDirectCallback(maybe.get(), updateStats);
            } else {
                inflight.decrementAndGet(); // quickly undo
            }
            return maybe;
        }

        int inflight() {
            return inflight.get();
        }
    }
}
