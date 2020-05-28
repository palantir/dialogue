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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
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

    private static final Comparator<SortableChannel> BY_SCORE = Comparator.comparingInt(SortableChannel::getScore);
    private static final Duration FAILURE_MEMORY = Duration.ofSeconds(30);
    private static final double FAILURE_WEIGHT = 10;

    /**
     * RTT_WEIGHT determines how sticky we are to the physically nearest node (as measured by RTT). If this is set
     * too high, then we may deliver suboptimal perf by sending all requests to a slow node that is physically nearby,
     * when there's actually a faster one further away.
     * If this is too low, then we may prematurely spill across AZs and pay the $ cost. Keep this lower than
     * {@link #FAILURE_WEIGHT} to ensure that a single 5xx makes a nearby node less attractive than a faraway node
     * that exhibited zero failures.
     */
    private static final double RTT_WEIGHT = 3;

    private final ImmutableList<MutableChannelWithStats> channels;
    private final Random random;
    private final Ticker clock;

    @Nullable
    private final RttSampler rttSampler;

    BalancedNodeSelectionStrategyChannel(
            ImmutableList<LimitedChannel> channels,
            Random random,
            Ticker ticker,
            TaggedMetricRegistry taggedMetrics,
            String channelName,
            RttSampling samplingEnabled) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.random = random;
        this.clock = ticker;
        this.rttSampler = samplingEnabled == RttSampling.DEFAULT_OFF ? null : new RttSampler(channels, clock);
        this.channels = IntStream.range(0, channels.size())
                .mapToObj(index -> new MutableChannelWithStats(
                        channels.get(index),
                        clock,
                        PerHostObservability.create(channels, taggedMetrics, channelName, index)))
                .collect(ImmutableList.toImmutableList());

        registerGauges(taggedMetrics, channelName, this.channels);
        log.debug("Initialized", SafeArg.of("count", channels.size()), UnsafeArg.of("channels", channels));
    }

    enum RttSampling {
        DEFAULT_OFF,
        ENABLED
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        // pre-shuffling is pretty important here, otherwise when there are no requests in flight, we'd
        // *always* prefer the first channel of the list, leading to a higher overall load.
        List<MutableChannelWithStats> preShuffled = shuffleImmutableList(channels, random);

        // TODO(dfox): P2C optimization when we have high number of nodes to save CPU?
        // http://www.eecs.harvard.edu/~michaelm/NEWWORK/postscripts/twosurvey.pdf
        SortableChannel[] sortableChannels = computeScores(rttSampler, preShuffled);
        Arrays.sort(sortableChannels, BY_SCORE);

        for (SortableChannel channel : sortableChannels) {
            Optional<ListenableFuture<Response>> maybe = channel.delegate.maybeExecute(endpoint, request);
            if (maybe.isPresent()) {
                if (rttSampler != null) {
                    rttSampler.maybeSampleRtts();
                }
                return maybe;
            }
        }

        return Optional.empty();
    }

    private static SortableChannel[] computeScores(
            @Nullable RttSampler rttSampler, List<MutableChannelWithStats> preShuffled) {
        SortableChannel[] snapshotArray = new SortableChannel[preShuffled.size()];

        if (rttSampler == null) {

            for (int i = 0; i < preShuffled.size(); i++) {
                SortableChannel snapshot = preShuffled.get(i).computeScore(0);
                snapshotArray[i] = snapshot;
            }
            return snapshotArray;

        } else {
            // Latency (rtt) is measured in nanos, which is a tricky unit to include in our 'score' because adding
            // it would dominate all the other data (which has the unit of 'num requests'). To avoid the need for a
            // conversion fudge-factor, we instead figure out where each rtt lies on the spectrum from bestRttNanos
            // to worstRttNanos, with 0 being best and 1 being worst. This ensures that if several nodes are all
            // within the same AZ and can return in ~1 ms but others return in ~5ms, the 1ms nodes will all have
            // a similar rttScore (near zero). Note, this can only be computed when we have all the snapshots in
            // front of us.
            long bestRttNanos = Long.MAX_VALUE;
            long worstRttNanos = 0;

            OptionalLong[] rtts = new OptionalLong[preShuffled.size()];
            for (int i = 0; i < preShuffled.size(); i++) {
                OptionalLong rtt = rttSampler.get(i).getRttNanos();
                rtts[i] = rtt;

                if (rtt.isPresent()) {
                    bestRttNanos = Math.min(bestRttNanos, rtt.getAsLong());
                    worstRttNanos = Math.max(worstRttNanos, rtt.getAsLong());
                }
            }
            long rttRange = worstRttNanos - bestRttNanos;

            for (int i = 0; i < preShuffled.size(); i++) {
                OptionalLong rtt = rtts[i];
                float rttSpectrum =
                        (rttRange > 0 && rtt.isPresent()) ? ((float) rtt.getAsLong() - bestRttNanos) / rttRange : 0;

                SortableChannel snapshot = preShuffled.get(i).computeScore(rttSpectrum);
                snapshotArray[i] = snapshot;
            }
            return snapshotArray;
        }
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }

    @VisibleForTesting
    Stream<SortableChannel> getScoresForTesting() {
        return Arrays.stream(computeScores(rttSampler, channels));
    }

    @Override
    public String toString() {
        return "BalancedNodeSelectionStrategyChannel{channels=" + channels + '}';
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
                        recentFailuresReservoir.update(FAILURE_WEIGHT);
                        observability.debugLogStatusFailure(response);
                    } else if (Responses.isClientError(response)) {
                        // We track 4xx responses because bugs in the server might cause one node to erroneously
                        // throw 401/403s when another node could actually return 200s. Empirically, healthy servers
                        // do actually return a continuous background rate of 4xx responses, so we weight these
                        // drastically less than 5xx responses.
                        recentFailuresReservoir.update(FAILURE_WEIGHT / 100);
                        observability.debugLogStatusFailure(response);
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    inflight.decrementAndGet();
                    recentFailuresReservoir.update(FAILURE_WEIGHT);
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

        SortableChannel computeScore(float rttSpectrum) {
            // it's important that scores are integers because if we kept the full double precision, then a single 4xx
            // would end up influencing host selection long beyond its intended lifespan in the absence of other data.
            int score = inflight.get()
                    + Ints.saturatedCast(Math.round(recentFailuresReservoir.get()))
                    + Ints.saturatedCast(Math.round(rttSpectrum * RTT_WEIGHT));

            return new SortableChannel(score, this);
        }

        @Override
        public String toString() {
            return "MutableChannelWithStats{"
                    + "delegate=" + delegate
                    + ", inflight=" + inflight
                    + ", recentFailures=" + recentFailuresReservoir
                    + '}';
        }
    }

    /**
     * A dedicated value class ensures safe sorting, as otherwise there's a risk that the inflight AtomicInteger
     * might change mid-sort, leading to undefined behaviour.
     */
    @VisibleForTesting
    static final class SortableChannel {
        private final int score;
        private final MutableChannelWithStats delegate;

        SortableChannel(int score, MutableChannelWithStats delegate) {
            this.delegate = delegate;
            this.score = score;
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
            log.info("Not registering gauges as there are too many nodes {}", SafeArg.of("count", channels.size()));
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
                    c -> c.computeScore(0).getScore(),
                    longStream -> {
                        long[] longs = longStream.toArray();
                        if (log.isInfoEnabled() && longs.length > 1) {
                            log.info(
                                    "Multiple ({}) objects contribute to the same gauge, taking the average "
                                            + "(beware this may be misleading) {} {}",
                                    SafeArg.of("count", longs.length),
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
