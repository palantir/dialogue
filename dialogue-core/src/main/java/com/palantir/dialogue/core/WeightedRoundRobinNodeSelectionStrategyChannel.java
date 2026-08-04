/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.BalancedScoreTracker.ChannelScoreInfo;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Chooses nodes weighted by the load each server reports about itself via the ORCA
 * {@code endpoint-load-metrics} response header (see {@link Responses#parseUtilization}). Where
 * {@link BalancedNodeSelectionStrategyChannel} balances request <i>count</i>, this balances server-reported
 * <i>load</i>, so it can route away from a node that is degraded (less capacity, a slow dependency, or resource-heavy
 * background work) even when that node is not receiving more requests than other available nodes.
 *
 * <p>Each host is assigned a weight {@code 1 / (utilization + failurePenalty + epsilon)} and requests are dispatched
 * in weighted-random order. Weighted-random ordering (rather than always preferring the single least-loaded host)
 * de-synchronizes independent clients so they don't all herd onto the same idle node between utilization readings.
 *
 * <p>Utilization is a point-in-time gauge, so we use the last reported value directly (no decay); decay is only
 * applied to discrete failures, via {@link BalancedScoreTracker}'s reservoir. Staleness is instead handled with three
 * guards borrowed from gRFC A58 ({@code weighted_round_robin}):
 * <ol>
 *   <li><b>Expiry</b> ({@link #UTILIZATION_EXPIRY_NANOS}): once a reading is this old we stop trusting it. With
 *       per-request reporting, a node we've routed away from is refreshed only occasionally, so the window is
 *       generous — a short one would "forget" a node is busy and re-probe it too eagerly.
 *   <li><b>Average fallback</b>: a host with no trusted reading (cold start, expired, or in blackout) uses the
 *       <i>average of the trusted peers' utilizations</i>, not a fixed constant. A fixed neutral value would make a
 *       node we correctly routed away from (so its reading went stale) look <i>better</i> than its still-busy peers,
 *       pulling a burst of traffic back onto it and oscillating; averaging keeps it peer-equal.
 *   <li><b>Blackout</b> ({@link #BLACKOUT_NANOS}): a node that has only just started reporting (cold start, or
 *       recovery after its reading expired) uses the average until it has reported continuously for the blackout, so
 *       one unrepresentative first sample can't stampede traffic onto it.
 * </ol>
 *
 * <p>This is a server-advertised, incubating strategy — see {@link DialogueNodeSelectionStrategy}. It reuses the
 * per-host bookkeeping of {@link BalancedScoreTracker} (inflight, a decaying failure reservoir, and, here,
 * utilization) but selects using weights rather than the {@link BalancedScoreTracker.ScoreSnapshot} score.
 */
final class WeightedRoundRobinNodeSelectionStrategyChannel implements LimitedChannel {
    private static final SafeLogger log = SafeLoggerFactory.get(WeightedRoundRobinNodeSelectionStrategyChannel.class);

    private static final Comparator<WeightSnapshot> BY_KEY_DESCENDING = Comparator.comparingDouble(
                    (WeightSnapshot snapshot) -> snapshot.key)
            .reversed();

    // Tunable knobs — starting values, to be settled empirically via WeightedRoundRobinSimulationTest.
    // epsilon caps the maximum weight (an idle node reporting 0 gets 1/epsilon) and prevents division by zero.
    private static final double EPSILON = 0.1;
    // Only used at cold start, before *any* host has a trusted reading; otherwise the fallback is the peer average.
    private static final double NEUTRAL_UTILIZATION = 0.5;
    // How much a unit of the decaying failure reservoir inflates effective load, down-weighting flapping nodes.
    private static final double FAILURE_UTILIZATION_SCALE = 0.1;
    // A58 weight_expiration_period. Generous because per-request reporting refreshes an avoided node only rarely.
    @VisibleForTesting
    static final long UTILIZATION_EXPIRY_NANOS = Duration.ofMinutes(3).toNanos();
    // A58 blackout_period: how long a newly-reporting node uses the peer average before its own reading is trusted.
    @VisibleForTesting
    static final long BLACKOUT_NANOS = Duration.ofSeconds(10).toNanos();

    private final BalancedScoreTracker tracker;
    private final ImmutableList<WeightedChannel> channels;
    private final Random random;
    private final Ticker clock;

    WeightedRoundRobinNodeSelectionStrategyChannel(
            ImmutableList<LimitedChannel> channels,
            Random random,
            Ticker ticker,
            TaggedMetricRegistry taggedMetrics,
            String channelName) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.random = random;
        this.clock = ticker;
        this.tracker = new BalancedScoreTracker(
                channels.size(), random, ticker, taggedMetrics, channelName, /* trackUtilization= */ true);
        this.channels = IntStream.range(0, channels.size())
                .mapToObj(index -> new WeightedChannel(
                        channels.get(index), tracker.channelStats().get(index)))
                .collect(ImmutableList.toImmutableList());
        log.debug("Initialized", SafeArg.of("count", channels.size()), UnsafeArg.of("channels", channels));
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        WeightSnapshot[] order = weightedRandomOrder();
        for (WeightSnapshot snapshot : order) {
            Optional<ListenableFuture<Response>> maybe =
                    StickyAttachments.maybeAddStickyToken(snapshot.channel, endpoint, request, limitEnforcement);
            if (maybe.isPresent()) {
                return maybe;
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the hosts in a random order weighted by each host's current weight, so the first accepting host is a
     * weighted-random choice. Snapshotting into value objects (like {@link BalancedScoreTracker.ScoreSnapshot}) keeps
     * the ordering stable even if utilization/failure counters change mid-sort.
     */
    private WeightSnapshot[] weightedRandomOrder() {
        long nowNanos = clock.read();
        int size = channels.size();

        // Pass 1: which hosts have a utilization reading we currently trust (fresh and past its blackout)? Hosts that
        // don't will fall back to the average of the trusted ones, so a stale/new node is never made to look better
        // than its busy peers (see class doc).
        OptionalDouble[] trustedUtilizations = new OptionalDouble[size];
        double trustedSum = 0;
        int trustedCount = 0;
        for (int i = 0; i < size; i++) {
            OptionalDouble utilization =
                    channels.get(i).channelInfo.usableUtilization(nowNanos, UTILIZATION_EXPIRY_NANOS, BLACKOUT_NANOS);
            trustedUtilizations[i] = utilization;
            if (utilization.isPresent()) {
                trustedSum += utilization.getAsDouble();
                trustedCount++;
            }
        }
        double fallbackUtilization = trustedCount > 0 ? trustedSum / trustedCount : NEUTRAL_UTILIZATION;

        // Pass 2: compute each host's weight and a random key ordering it.
        WeightSnapshot[] snapshots = new WeightSnapshot[size];
        for (int i = 0; i < size; i++) {
            WeightedChannel channel = channels.get(i);
            double utilization = trustedUtilizations[i].orElse(fallbackUtilization);
            double effectiveLoad = utilization + FAILURE_UTILIZATION_SCALE * channel.channelInfo.recentFailures();
            double weight = 1.0 / (effectiveLoad + EPSILON);
            // Efraimidis-Spirakis weighted random sampling without replacement: draw key = ln(u)/weight and order by
            // descending key. This yields a permutation in which each host's chance of coming first is proportional
            // to its weight. Using the log form keeps it numerically stable across a wide weight range.
            double key = Math.log(random.nextDouble()) / weight;
            snapshots[i] = new WeightSnapshot(key, channel);
        }
        Arrays.sort(snapshots, BY_KEY_DESCENDING);
        return snapshots;
    }

    private static final class WeightSnapshot {
        private final double key;
        private final WeightedChannel channel;

        WeightSnapshot(double key, WeightedChannel channel) {
            this.key = key;
            this.channel = channel;
        }
    }

    private static final class WeightedChannel implements LimitedChannel {
        private final LimitedChannel delegate;
        private final ChannelScoreInfo channelInfo;

        WeightedChannel(LimitedChannel delegate, ChannelScoreInfo channelInfo) {
            this.delegate = delegate;
            this.channelInfo = channelInfo;
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(
                Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
            channelInfo.startRequest();

            Optional<ListenableFuture<Response>> maybe = delegate.maybeExecute(endpoint, request, limitEnforcement);

            if (maybe.isPresent()) {
                channelInfo.observability().markRequestMade();
                DialogueFutures.addDirectCallback(maybe.get(), channelInfo);
                return maybe;
            } else {
                channelInfo.undoStartRequest();
            }
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "WeightedChannel{delegate=" + delegate + ", channelInfo=" + channelInfo + '}';
        }
    }

    @Override
    public String toString() {
        return "WeightedRoundRobinNodeSelectionStrategyChannel{channels=" + channels + ", tracker=" + tracker + '}';
    }
}
