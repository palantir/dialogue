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
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Chooses nodes based on stats about each channel, i.e. how many requests are currently
 * being served, how many failures have been seen in the last few seconds.
 *
 * This is intended to be a strict improvement over Round Robin and Random Selection which can leave fast servers
 * underutilized, as it sends the same number to both a slow and fast node. It is *not* appropriate for transactional
 * workloads (where n requests must all land on the same server) or scenarios where cache warming is very important.
 * {@link PinUntilErrorNodeSelectionStrategyChannel} remains the best choice for these.
 */
final class BalancedScoreTracker {
    private static final SafeLogger log = SafeLoggerFactory.get(BalancedScoreTracker.class);

    private static final Comparator<ScoreSnapshot> BY_SCORE = Comparator.comparingInt(ScoreSnapshot::getScore);
    private static final Duration FAILURE_MEMORY = Duration.ofSeconds(30);
    private static final double FAILURE_WEIGHT = 10;

    private final ImmutableList<ChannelScoreInfo> channelStats;
    private final Random random;
    private final Ticker clock;

    BalancedScoreTracker(
            int channelCount, Random random, Ticker ticker, TaggedMetricRegistry taggedMetrics, String channelName) {
        Preconditions.checkState(channelCount >= 1, "At least one channel required");
        this.random = random;
        this.clock = ticker;
        this.channelStats = IntStream.range(0, channelCount)
                .mapToObj(index -> new ChannelScoreInfo(
                        index, clock, PerHostObservability.create(channelCount, taggedMetrics, channelName, index)))
                .collect(ImmutableList.toImmutableList());

        registerGauges(taggedMetrics, channelName, channelStats);
    }

    /**
     * Returns all channels, ordered with the best score first. Called on every request, so needs to be performant!
     * Callers *must* use the {@link ChannelScoreInfo#startRequest} and {@link ChannelScoreInfo#onSuccess} etc
     * methods to feed information back into the tracker.
     */
    ScoreSnapshot[] getSnapshotsInOrderOfIncreasingScore() {
        // pre-shuffling is pretty important here, otherwise when there are no requests in flight, we'd
        // *always* prefer the first channel of the list, leading to a higher overall load.
        List<ChannelScoreInfo> shuffledMutableStats = shuffleImmutableList(channelStats, random);

        ScoreSnapshot[] snapshotArray = new ScoreSnapshot[shuffledMutableStats.size()];
        for (int i = 0; i < snapshotArray.length; i++) {
            snapshotArray[i] = shuffledMutableStats.get(i).computeScoreSnapshot();
        }

        Arrays.sort(snapshotArray, BY_SCORE);

        return snapshotArray;
    }

    public ChannelScoreInfo getSingleBestChannelByScore() {
        // TODO(dfox): in theory we could optimize this by just looping manually and keeping track of the max
        return getSnapshotsInOrderOfIncreasingScore()[0].getDelegate();
    }

    @VisibleForTesting
    IntStream getScoresForTesting() {
        return channelStats.stream().mapToInt(c -> c.computeScoreSnapshot().score);
    }

    ImmutableList<ChannelScoreInfo> channelStats() {
        return channelStats;
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }

    @Override
    public String toString() {
        return "BalancedScoreTracker{" + channelStats + '}';
    }

    public static final class ChannelScoreInfo implements FutureCallback<Response> {
        private final int hostIndex;
        private final PerHostObservability observability;

        private final AtomicInteger inflight = new AtomicInteger(0);

        /**
         * We keep track of failures within a time window to do well in scenarios where an unhealthy server returns
         * errors much faster than healthy nodes can serve good responses. See
         * <code>SimulationTest.fast_503s_then_revert</code>.
         */
        private final CoarseExponentialDecayReservoir recentFailuresReservoir;

        ChannelScoreInfo(int hostIndex, Ticker clock, PerHostObservability observability) {
            this.hostIndex = hostIndex;
            this.recentFailuresReservoir = new CoarseExponentialDecayReservoir(clock::read, FAILURE_MEMORY);
            this.observability = observability;
        }

        public void startRequest() {
            inflight.incrementAndGet();
        }

        public PerHostObservability observability() {
            return observability;
        }

        /** Only necessary for the {@link LimitedChannel} case, where a request might just be refused. */
        public void undoStartRequest() {
            inflight.decrementAndGet();
        }

        @Override
        public void onSuccess(Response response) {
            inflight.decrementAndGet();

            if (Responses.isQosDueToCustom(response)) {
                // The server has marked this QoS exception as something that the balanced score
                // tracker cannot understand.
                return;
            }
            if (isGlobalQosStatus(response) || Responses.isServerErrorRange(response)) {
                recentFailuresReservoir.update(FAILURE_WEIGHT);
                observability.debugLogStatusFailure(response);
            } else if (Responses.isClientError(response) || Responses.isQosStatus(response)) {
                // We track 4xx responses because bugs in the server might cause one node to erroneously
                // throw 401/403s when another node could actually return 200s. Empirically, healthy servers
                // do actually return a continuous background rate of 4xx responses, so we weight these
                // drastically less than 5xx responses.
                recentFailuresReservoir.update(FAILURE_WEIGHT / 100);
                observability.debugLogStatusFailure(response);
            }
        }

        /** 429 responses suggest an endpoint is overwhelmed, not necessarily the entire service. */
        private static boolean isGlobalQosStatus(Response response) {
            return Responses.isQosStatus(response) && !Responses.isTooManyRequests(response);
        }

        public int channelIndex() {
            return hostIndex;
        }

        @Override
        public void onFailure(Throwable throwable) {
            inflight.decrementAndGet();
            recentFailuresReservoir.update(FAILURE_WEIGHT);
            observability.debugLogThrowableFailure(recentFailuresReservoir, throwable);
        }

        private ScoreSnapshot computeScoreSnapshot() {
            int requestsInflight = inflight.get();
            double failureReservoir = recentFailuresReservoir.get();

            // it's important that scores are integers because if we kept the full double precision, then a single 4xx
            // would end up influencing host selection long beyond its intended lifespan in the absence of other data.
            int score = requestsInflight + Ints.saturatedCast(Math.round(failureReservoir));

            observability.traceLogComputedScore(requestsInflight, failureReservoir, score);
            return new ScoreSnapshot(score, requestsInflight, this);
        }

        @Override
        public String toString() {
            return "ChannelScoreInfo{"
                    + "hostIndex=" + hostIndex
                    + ", inflight=" + inflight
                    + ", recentFailures=" + recentFailuresReservoir
                    + '}';
        }
    }

    /**
     * A dedicated value class ensures safe sorting, as otherwise there's a risk that the inflight AtomicInteger
     * might change mid-sort, leading to undefined behaviour.
     */
    static final class ScoreSnapshot {
        private final int score;
        private final int inflight;
        private final ChannelScoreInfo delegate;

        ScoreSnapshot(int score, int inflight, ChannelScoreInfo delegate) {
            this.score = score;
            this.inflight = inflight;
            this.delegate = delegate;
        }

        int getScore() {
            return score;
        }

        int getInflight() {
            return inflight;
        }

        ChannelScoreInfo getDelegate() {
            return delegate;
        }

        @Override
        public String toString() {
            return "ScoreSnapshot{score=" + score + ", delegate=" + delegate + '}';
        }
    }

    private static void registerGauges(
            TaggedMetricRegistry taggedMetrics, String channelName, ImmutableList<ChannelScoreInfo> channels) {
        if (channels.size() > 10) {
            log.info("Not registering gauges as there are too many nodes {}", SafeArg.of("count", channels.size()));
            return;
        }

        for (int hostIndex = 0; hostIndex < channels.size(); hostIndex++) {
            MetricName metricName = DialogueBalancedMetrics.of(taggedMetrics)
                    .score()
                    .channelName(channelName)
                    .hostIndex(Integer.toString(hostIndex))
                    .buildMetricName();
            // Weak gauge ensures this object can be GCd. Itherwise the tagged metric registry could hold the last ref!
            // Defensive averaging for the possibility that people create multiple channels with the same channelName.
            DialogueInternalWeakReducingGauge.getOrCreate(
                    taggedMetrics,
                    metricName,
                    c -> c.computeScoreSnapshot().getScore(),
                    longStream -> {
                        long[] longs = longStream.toArray();
                        if (log.isInfoEnabled()
                                && longs.length > 1
                                && LongStream.of(longs).distinct().count() > 1) {
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

    public abstract static class PerHostObservability {
        private final SafeArg<String> channelName;
        private final SafeArg<Integer> hostIndex;

        PerHostObservability(String channelName, int hostIndex) {
            this.channelName = SafeArg.of("channelName", channelName);
            this.hostIndex = SafeArg.of("hostIndex", hostIndex);
        }

        public abstract void markRequestMade();

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

        void traceLogComputedScore(int inflight, double failures, int score) {
            if (log.isTraceEnabled()) {
                log.trace(
                        "Computed score ({} {}) {}",
                        channelName,
                        hostIndex,
                        SafeArg.of("score", score),
                        SafeArg.of("inflight", inflight),
                        SafeArg.of("failures", failures));
            }
        }

        static PerHostObservability create(
                int numChannels, TaggedMetricRegistry taggedMetrics, String channelName, int index) {
            // hard limit ensures we don't create unbounded tags
            if (numChannels > 10) {
                return new PerHostObservability(channelName, index) {
                    @Override
                    public void markRequestMade() {
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
