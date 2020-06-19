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
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.BalancedNodeSelectionStrategyChannel.RttSampling;
import com.palantir.logsafe.Preconditions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses nodes based on stats about each channel, i.e. how many requests are currently
 * being served, how many failures have been seen in the last few seconds and (optionally) also what the best latency
 * to each node is. Use {@link RttSampling#ENABLED} to switch this on.
 *
 * This is intended to be a strict improvement over Round Robin and Random Selection which can leave fast servers
 * underutilized, as it sends the same number to both a slow and fast node. It is *not* appropriate for transactional
 * workloads (where n requests must all land on the same server) or scenarios where cache warming is very important.
 * {@link PinUntilErrorNodeSelectionStrategyChannel} remains the best choice for these.
 */
final class BalancedScoreTracker {
    private static final Logger log = LoggerFactory.getLogger(BalancedNodeSelectionStrategyChannel.class);

    private static final Comparator<Snapshot> BY_SCORE = Comparator.comparingInt(Snapshot::getScore);
    private static final Duration FAILURE_MEMORY = Duration.ofSeconds(30);
    private static final double FAILURE_WEIGHT = 10;

    private final ImmutableList<MutableStats> stats;
    private final Random random;
    private final Ticker clock;

    BalancedScoreTracker(int channelCount, Random random, Ticker ticker) {
        Preconditions.checkState(channelCount >= 2, "At least two channels required");
        this.random = random;
        this.clock = ticker;
        this.stats = IntStream.range(0, channelCount)
                .mapToObj(index -> new MutableStats(index, clock))
                .collect(ImmutableList.toImmutableList());
    }

    public ScoreTracker getBestHost() {
        return getChannelsByScore()[0];
    }

    public ScoreTracker[] getChannelsByScore() {
        // pre-shuffling is pretty important here, otherwise when there are no requests in flight, we'd
        // *always* prefer the first channel of the list, leading to a higher overall load.
        List<MutableStats> shuffledMutableStats = shuffleImmutableList(stats, random);

        Snapshot[] snapshotArray = new Snapshot[shuffledMutableStats.size()];
        for (int i = 0; i < snapshotArray.length; i++) {
            snapshotArray[i] = shuffledMutableStats.get(i).computeScore();
        }

        Arrays.sort(snapshotArray, BY_SCORE);

        ScoreTracker[] returnArray = new ScoreTracker[snapshotArray.length];
        for (int i = 0; i < snapshotArray.length; i++) {
            returnArray[i] = snapshotArray[i].delegate;
        }
        return returnArray;
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }

    @Override
    public String toString() {
        return "NewBalanced{channels=" + stats + '}';
    }

    interface ScoreTracker extends FutureCallback<Response> {
        int hostIndex();

        void onFailure(Throwable _throwable);

        void onSuccess(Response response);

        void undoStartRequest();

        void startRequest();
    }

    private static final class MutableStats implements ScoreTracker {
        private final int hostIndex;

        private final AtomicInteger inflight = new AtomicInteger(0);

        /**
         * We keep track of failures within a time window to do well in scenarios where an unhealthy server returns
         * errors much faster than healthy nodes can serve good responses. See
         * <code>SimulationTest.fast_503s_then_revert</code>.
         */
        private final CoarseExponentialDecayReservoir recentFailuresReservoir;

        MutableStats(int hostIndex, Ticker clock) {
            this.hostIndex = hostIndex;
            this.recentFailuresReservoir = new CoarseExponentialDecayReservoir(clock::read, FAILURE_MEMORY);
        }

        @Override
        public void startRequest() {
            inflight.incrementAndGet();
        }

        @Override
        public void undoStartRequest() {
            inflight.decrementAndGet();
        }

        @Override
        public void onSuccess(Response response) {
            inflight.decrementAndGet();

            if (Responses.isQosStatus(response) || Responses.isServerError(response)) {
                recentFailuresReservoir.update(FAILURE_WEIGHT);
            } else if (Responses.isClientError(response)) {
                // We track 4xx responses because bugs in the server might cause one node to erroneously
                // throw 401/403s when another node could actually return 200s. Empirically, healthy servers
                // do actually return a continuous background rate of 4xx responses, so we weight these
                // drastically less than 5xx responses.
                recentFailuresReservoir.update(FAILURE_WEIGHT / 100);
            }
        }

        @Override
        public int hostIndex() {
            return hostIndex;
        }

        @Override
        public void onFailure(Throwable _throwable) {
            inflight.decrementAndGet();
            recentFailuresReservoir.update(FAILURE_WEIGHT);
        }

        private Snapshot computeScore() {
            int requestsInflight = inflight.get();
            double failureReservoir = recentFailuresReservoir.get();

            // it's important that scores are integers because if we kept the full double precision, then a single 4xx
            // would end up influencing host selection long beyond its intended lifespan in the absence of other data.
            int score = requestsInflight + Ints.saturatedCast(Math.round(failureReservoir));

            return new Snapshot(score, this);
        }

        @Override
        public String toString() {
            return "MutableStats{"
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
    private static final class Snapshot {
        private final int score;
        private final MutableStats delegate;

        Snapshot(int score, MutableStats delegate) {
            this.score = score;
            this.delegate = delegate;
        }

        int getScore() {
            return score;
        }

        @Override
        public String toString() {
            return "Snapshot{score=" + score + ", delegate=" + delegate + '}';
        }
    }
}
