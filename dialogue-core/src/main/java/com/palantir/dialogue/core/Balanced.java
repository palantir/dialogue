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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
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

    private static final Duration FAILURE_MEMORY = Duration.ofSeconds(30);

    @VisibleForTesting
    static final Comparator<ChannelStats> RANKING_HEURISTIC = Comparator.comparing(channel -> {
        return channel.inflight + 10 * (long) channel.recentFailures;
    });

    private final ImmutableList<MutableChannelWithStats> channels;
    private final Random random;
    private final Ticker clock;

    Balanced(ImmutableList<LimitedChannel> channels, Random random, Ticker ticker) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.random = random;
        this.clock = ticker;
        this.channels = channels.stream()
                .map(channel -> new MutableChannelWithStats(channel, clock))
                .collect(ImmutableList.toImmutableList());

        log.debug("Initialized", SafeArg.of("count", channels.size()), UnsafeArg.of("channels", channels));
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        // pre-shuffling is pretty important here, otherwise when there are no requests in flight, we'd
        // *always* prefer the first channel of the list, leading to a higher overall load.
        List<MutableChannelWithStats> preShuffled = shuffleImmutableList(channels, random);

        // TODO(dfox): P2C optimization when we have high number of nodes to save CPU?
        // http://www.eecs.harvard.edu/~michaelm/NEWWORK/postscripts/twosurvey.pdf
        return preShuffled.stream()
                .map(MutableChannelWithStats::immutableSnapshot) // necessary for safe sorting
                .sorted(RANKING_HEURISTIC)
                .map(channel -> channel.delegate.maybeExecute(endpoint, request))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }

    static final class MutableChannelWithStats implements LimitedChannel {
        private final LimitedChannel delegate;

        @VisibleForTesting
        final AtomicInteger inflight = new AtomicInteger(0);

        /**
         * We keep track of failures within a time window to do well in scenarios where an unhealthy server returns
         * errors much faster than healthy nodes can serve good responses. See
         * <code>SimulationTest.fast_503s_then_revert</code>.
         */
        @VisibleForTesting
        final CoarseExponentialDecay recentFailures;

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

        MutableChannelWithStats(LimitedChannel delegate, Ticker clock) {
            this.delegate = delegate;
            this.recentFailures = new CoarseExponentialDecay(clock::read, FAILURE_MEMORY);
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

        ChannelStats immutableSnapshot() {
            return new ChannelStats(inflight.get(), recentFailures.get(), this);
        }
    }

    /**
     * A dedicated immutable class ensures safe sorting, as otherwise there's a risk that the inflight AtomicInteger
     * might change mid-sort, leading to undefined behaviour.
     */
    static class ChannelStats {
        private final long inflight;
        private final int recentFailures;

        @VisibleForTesting
        final MutableChannelWithStats delegate;

        ChannelStats(long inflight, int recentFailures, MutableChannelWithStats delegate) {
            this.inflight = inflight;
            this.recentFailures = recentFailures;
            this.delegate = delegate;
        }

        @Override
        public String toString() {
            return "ChannelStats{inflight="
                    + inflight + ", recentFailures="
                    + recentFailures + ", delegate="
                    + delegate + '}';
        }
    }
}
