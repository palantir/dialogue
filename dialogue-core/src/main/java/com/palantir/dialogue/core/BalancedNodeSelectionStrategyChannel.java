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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
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
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.concurrent.ThreadSafe;
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
    private static final Request BLANK_REQUEST = Request.builder().build();

    private static final Comparator<SortableChannel> BY_SCORE =
            Comparator.comparingInt(SortableChannel::getScore).thenComparingLong(SortableChannel::getRtt);
    private static final Duration FAILURE_MEMORY = Duration.ofSeconds(30);
    private static final double FAILURE_WEIGHT = 10;

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
        // pre-shuffling is pretty important here, otherwise when there are no requests in flight, we'd
        // *always* prefer the first channel of the list, leading to a higher overall load.
        List<MutableChannelWithStats> preShuffled = shuffleImmutableList(channels, random);

        // TODO(dfox): P2C optimization when we have high number of nodes to save CPU?
        // http://www.eecs.harvard.edu/~michaelm/NEWWORK/postscripts/twosurvey.pdf
        SortableChannel[] sortedChannels = sortByScore(preShuffled);

        for (SortableChannel channel : sortedChannels) {
            Optional<ListenableFuture<Response>> maybe = channel.delegate.maybeExecute(endpoint, request);
            if (maybe.isPresent()) {
                sampleRttsForAllChannels();
                return maybe;
            }
        }

        return Optional.empty();
    }

    private void sampleRttsForAllChannels() {
        // TODO(dfox): don't do this 100% of the time
        // if (random.nextDouble() > 0.01f) {
        //     return;
        // }

        List<ListenableFuture<Long>> results = new ArrayList<>();
        for (MutableChannelWithStats channel : channels) {
            long attempt1Before = clock.read();

            Optional<ListenableFuture<Response>> attempt1 =
                    channel.delegate.maybeExecute(RttEndpoint.INSTANCE, BLANK_REQUEST);
            if (!attempt1.isPresent()) {
                results.add(Futures.immediateFuture(Long.MAX_VALUE));
                continue;
            }

            ListenableFuture<Long> attempt1Duration = Futures.transform(
                    attempt1.get(), response1 -> clock.read() - attempt1Before, MoreExecutors.directExecutor());

            results.add(Futures.transformAsync(
                    attempt1Duration,
                    attempt1Nanos -> {
                        long attempt2Before = clock.read();
                        Optional<ListenableFuture<Response>> attempt2 =
                                channel.delegate.maybeExecute(RttEndpoint.INSTANCE, BLANK_REQUEST);
                        if (!attempt2.isPresent()) {
                            return Futures.immediateFuture(Long.MAX_VALUE); // signifies we couldn't get two samples
                        }

                        return Futures.transform(
                                attempt2.get(),
                                response2 -> {
                                    long attempt2Nanos = clock.read() - attempt2Before;
                                    // taking min of two attempts to exclude samples that incurred a TLS handshake
                                    long newMeasurement = Math.min(attempt1Nanos, attempt2Nanos);
                                    channel.rtt.addMeasurement(newMeasurement);
                                    return newMeasurement;
                                },
                                MoreExecutors.directExecutor());
                    },
                    MoreExecutors.directExecutor()));
        }
        if (log.isInfoEnabled()) {
            DialogueFutures.addDirectCallback(Futures.allAsList(results), new FutureCallback<List<Long>>() {
                @Override
                public void onSuccess(List<Long> result) {
                    List<Long> millis =
                            result.stream().map(TimeUnit.NANOSECONDS::toMillis).collect(Collectors.toList());
                    log.info("RTTs {} {}", SafeArg.of("nanos", result), SafeArg.of("millis", millis));
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.info("Throwable", throwable);
                }
            });
        }
    }

    private static SortableChannel[] sortByScore(List<MutableChannelWithStats> preShuffled) {
        SortableChannel[] sorted = new SortableChannel[preShuffled.size()];
        for (int i = 0; i < preShuffled.size(); i++) {
            sorted[i] = preShuffled.get(i).computeScore();
        }
        Arrays.sort(sorted, BY_SCORE);
        return sorted;
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }

    @VisibleForTesting
    IntStream getScores() {
        return channels.stream().mapToInt(c -> c.computeScore().score);
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
        private final RoundTripTimeMeasurement rtt = new RoundTripTimeMeasurement();

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
            if (!maybe.isPresent()) {
                inflight.decrementAndGet(); // quickly undo
                return Optional.empty();
            }

            DialogueFutures.addDirectCallback(maybe.get(), updateStats);
            observability.markRequestMade();
            return maybe;
        }

        SortableChannel computeScore() {
            int requestsInflight = inflight.get();
            double failureReservoir = recentFailuresReservoir.get();

            // it's important that scores are integers because if we kept the full double precision, then a single 4xx
            // would end up influencing host selection long beyond its intended lifespan in the absence of other data.
            int score = requestsInflight + Ints.saturatedCast(Math.round(failureReservoir));

            observability.debugLogComputedScore(requestsInflight, failureReservoir, score);
            return new SortableChannel(score, rtt.getNanos(), this);
        }

        @Override
        public String toString() {
            return "MutableChannelWithStats{score=" + computeScore().score
                    + ", inflight=" + inflight
                    + ", recentFailures=" + recentFailuresReservoir
                    + ", delegate=" + delegate
                    + '}';
        }
    }

    /**
     * A dedicated immutable class ensures safe sorting, as otherwise there's a risk that the inflight AtomicInteger
     * might change mid-sort, leading to undefined behaviour.
     */
    private static final class SortableChannel {
        private final int score;
        private final long rtt;
        private final MutableChannelWithStats delegate;

        SortableChannel(int score, long rtt, MutableChannelWithStats delegate) {
            this.score = score;
            this.rtt = rtt;
            this.delegate = delegate;
        }

        int getScore() {
            return score;
        }

        long getRtt() {
            return rtt;
        }

        @Override
        public String toString() {
            return "SortableChannel{" + "score=" + score + ", rtt=" + rtt + ", delegate=" + delegate + '}';
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
                    c -> c.computeScore().getScore(),
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

        void debugLogComputedScore(int inflight, double failures, int score) {
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

    private enum RttEndpoint implements Endpoint {
        INSTANCE;

        @Override
        public void renderPath(Map<String, String> _params, UrlBuilder _url) {}

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.OPTIONS;
        }

        @Override
        public String serviceName() {
            return "Balanced";
        }

        @Override
        public String endpointName() {
            return "rtt";
        }

        @Override
        public String version() {
            return "0.0.0";
        }
    }

    @VisibleForTesting
    @ThreadSafe
    static class RoundTripTimeMeasurement {

        // TODO(dfox): switch to some exponentially decaying thingy?
        // TODO(dfox): can we exclude outlier measurements that probably include TLS handshakes?
        private volatile float rttNanos = 0;
        private volatile long numSamples = 0;

        long getNanos() {
            return (long) rttNanos;
        }

        synchronized void addMeasurement(long newMeasurement) {
            float denominator = (float) numSamples / (numSamples + 1);
            this.rttNanos = rttNanos * denominator + (float) newMeasurement / (numSamples + 1);
            this.numSamples = numSamples + 1;
        }

        @Override
        public String toString() {
            return "RoundTripTimeMeasurement{" + "rttNanos=" + rttNanos + ", numSamples=" + numSamples + '}';
        }
    }
}
