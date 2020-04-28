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

import com.codahale.metrics.Meter;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses nodes based on stats about each channel, i.e. how many requests are currently
 * being served and also how many failures have been seen in the last few seconds.
 *
 * This is intended to be a strict improvement over Round Robin and Random Selection which can leave fast servers
 * underutilized, as it sends the same number to both a slow and fast node. It is *not* appropriate for transactional
 * workloads (where n requests must all land on the same server) or scenarios where cache warming is very important.
 * {@link PinUntilErrorNodeSelectionStrategyChannel} remains the best choice for these.
 */
final class BalancedNodeSelectionStrategyChannel implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(BalancedNodeSelectionStrategyChannel.class);

    private static final Duration FAILURE_MEMORY = Duration.ofSeconds(30);

    private final ImmutableList<MutableChannelWithStats> channels;
    private final Random random;
    private final Ticker clock;

    BalancedNodeSelectionStrategyChannel(
            ImmutableList<LimitedChannel> channels,
            Random random,
            Ticker ticker,
            TaggedMetricRegistry taggedMetrics,
            String channelName) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.random = random;
        this.clock = ticker;
        this.channels = IntStream.range(0, channels.size())
                .mapToObj(index -> new MutableChannelWithStats(
                        channels.get(index),
                        clock,
                        PerHostObservability.create(channels, taggedMetrics, channelName, index)))
                .collect(ImmutableList.toImmutableList());

        registerGauges(taggedMetrics, channelName, this.channels);
        log.debug("Initialized", SafeArg.of("count", channels.size()), UnsafeArg.of("channels", channels));
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        MutableChannelWithStats[] array = channels.toArray(new MutableChannelWithStats[] {});

        int destIndex = 0;
        SortableChannel choice1 = randomSwapToFront(array, destIndex++).computeScore();

        // Power of 2 choices (http://www.eecs.harvard.edu/~michaelm/NEWWORK/postscripts/twosurvey.pdf)
        // It's not necessary to sort the entire array, we just need to pick a second channel at random each time
        // and we'll get a close-enough approximation to full sorting, without spending the CPU.
        while (destIndex < array.length) {
            SortableChannel choice2 = randomSwapToFront(array, destIndex++).computeScore();

            // low scores = good
            SortableChannel best = choice1.score <= choice2.score ? choice1 : choice2;
            SortableChannel notBest = best == choice1 ? choice2 : choice1;

            Optional<ListenableFuture<Response>> maybe = best.delegate.maybeExecute(endpoint, request);
            if (maybe.isPresent()) {
                return maybe;
            }

            choice1 = notBest; // keep this channel around for the next iteration as we haven't tried it yet!
        }

        return choice1.delegate.maybeExecute(endpoint, request);
    }

    private <T> T randomSwapToFront(T[] array, int dest) {
        int index = random.nextInt(array.length - dest);
        T temp = array[dest];
        array[dest] = array[dest + index];
        array[dest + index] = temp;
        return array[dest];
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }

    private static final class MutableChannelWithStats implements LimitedChannel {
        private final LimitedChannel delegate;
        private final FutureCallback<Response> updateStats;
        private final PerHostObservability observability;

        private final AtomicInteger inflight = new AtomicInteger(0);

        /**
         * We keep track of failures within a time window to do well in scenarios where an unhealthy server returns
         * errors much faster than healthy nodes can serve good responses. See
         * <code>SimulationTest.fast_503s_then_revert</code>.
         */
        private final CoarseExponentialDecayReservoir recentFailuresReservoir;

        MutableChannelWithStats(LimitedChannel delegate, Ticker clock, PerHostObservability observability) {
            this.delegate = delegate;
            this.recentFailuresReservoir = new CoarseExponentialDecayReservoir(clock::read, FAILURE_MEMORY);
            this.observability = observability;
            // Saves one allocation on each network call
            this.updateStats = new FutureCallback<Response>() {
                @Override
                public void onSuccess(Response response) {
                    inflight.decrementAndGet();
                    if (Responses.isQosStatus(response) || Responses.isServerError(response)) {
                        recentFailuresReservoir.update(1);
                        observability.debugLogStatusFailure(response);
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    inflight.decrementAndGet();
                    recentFailuresReservoir.update(1);
                    observability.debugLogThrowableFailure(recentFailuresReservoir, throwable);
                }
            };
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
            inflight.incrementAndGet();
            Optional<ListenableFuture<Response>> maybe = delegate.maybeExecute(endpoint, request);
            if (maybe.isPresent()) {
                DialogueFutures.addDirectCallback(maybe.get(), updateStats);
                observability.markRequestMade();
            } else {
                inflight.decrementAndGet(); // quickly undo
            }
            return maybe;
        }

        SortableChannel computeScore() {
            int requestsInflight = inflight.get();
            int recentFailures = recentFailuresReservoir.get();

            int score = requestsInflight + 10 * recentFailures;

            observability.debugLogComputedScore(requestsInflight, recentFailures, score);
            return new SortableChannel(score, this);
        }

        @Override
        public String toString() {
            return "MutableChannelWithStats{inflight="
                    + inflight + ", recentFailures="
                    + recentFailuresReservoir + ", delegate="
                    + delegate + '}';
        }
    }

    /**
     * A dedicated immutable class ensures safe sorting, as otherwise there's a risk that the inflight AtomicInteger
     * might change mid-sort, leading to undefined behaviour.
     */
    private static final class SortableChannel {
        private final int score;
        private final MutableChannelWithStats delegate;

        SortableChannel(int score, MutableChannelWithStats delegate) {
            this.score = score;
            this.delegate = delegate;
        }

        int getScore() {
            return score;
        }

        @Override
        public String toString() {
            return "SortableChannel{score=" + score + ", delegate=" + delegate + '}';
        }
    }

    private static void registerGauges(
            TaggedMetricRegistry taggedMetrics, String channelName, ImmutableList<MutableChannelWithStats> channels) {
        if (channels.size() > 10) {
            log.info("Not registering gauges as there are too many nodes", SafeArg.of("count", channels.size()));
            return;
        }

        for (int hostIndex = 0; hostIndex < channels.size(); hostIndex++) {
            MetricName metricName = MetricName.builder()
                    .safeName("dialogue.balanced.score")
                    .putSafeTags("channel-name", channelName)
                    .putSafeTags("hostIndex", Integer.toString(hostIndex))
                    .build();
            // Weak gauge ensures this object can be GCd. Itherwise the tagged metric registry could hold the last ref!
            // Defensive averaging for the possibility that people create multiple channels with the same channelName.
            DialogueInternalWeakReducingGauge.getOrCreate(
                    taggedMetrics,
                    metricName,
                    c -> c.computeScore().getScore(),
                    longStream -> {
                        long[] longs = longStream.toArray();
                        if (longs.length > 1) {
                            log.warn(
                                    "Multiple objects contribute to the same gauge, taking the average "
                                            + "(beware this may be misleading)",
                                    SafeArg.of("metricName", metricName),
                                    SafeArg.of("values", Arrays.toString(longs)));
                        }
                        return Arrays.stream(longs).average().orElse(0);
                    },
                    channels.get(hostIndex));
        }
    }

    private abstract static class PerHostObservability {
        private final SafeArg<String> channelName;
        private final SafeArg<Integer> hostIndex;

        PerHostObservability(String channelName, int hostIndex) {
            this.channelName = SafeArg.of("channelName", channelName);
            this.hostIndex = SafeArg.of("hostIndex", hostIndex);
        }

        abstract void markRequestMade();

        void debugLogThrowableFailure(CoarseExponentialDecayReservoir reservoir, Throwable throwable) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Recorded recent failure (throwable)",
                        channelName,
                        hostIndex,
                        SafeArg.of("recentFailures", reservoir.get()),
                        throwable);
            }
        }

        void debugLogStatusFailure(Response response) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Recorded recent failure (status)",
                        channelName,
                        hostIndex,
                        SafeArg.of("status", response.code()));
            }
        }

        void debugLogComputedScore(int inflight, int failures, int score) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Computed score",
                        channelName,
                        hostIndex,
                        SafeArg.of("score", score),
                        SafeArg.of("inflight", inflight),
                        SafeArg.of("failures", failures));
            }
        }

        static PerHostObservability create(
                ImmutableList<LimitedChannel> channels,
                TaggedMetricRegistry taggedMetrics,
                String channelName,
                int index) {
            // hard limit ensures we don't create unbounded tags
            if (channels.size() > 10) {
                return new PerHostObservability(channelName, index) {
                    @Override
                    void markRequestMade() {
                        // noop
                    }
                };
            }

            // pre-creating meters avoids allocating builders on hot codepaths
            Meter meter = DialogueRoundrobinMetrics.of(taggedMetrics)
                    .success()
                    .channelName(channelName)
                    .hostIndex(Integer.toString(index))
                    .build();
            return new PerHostObservability(channelName, index) {
                @Override
                public void markRequestMade() {
                    meter.mark();
                }
            };
        }
    }
}
