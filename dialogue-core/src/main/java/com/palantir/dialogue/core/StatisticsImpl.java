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

import com.codahale.metrics.Clock;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Reservoir;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heuristic: probability of success in a (endpoint,upstream,version) combo increases slowly.
 * <ul>
 * <li>successful responses (200s) increase confidence by 1
 * <li>failures (500s) decrease confidence by 10
 * <li>client side exceptions decrease confidence by 20
 * <li>confidence decays over time
 * </ul>
 *
 * TODO(dfox): balance the feedback loop of confidence with spreading load to other nodes
 */
@SuppressWarnings("Slf4jLogsafeArgs")
final class StatisticsImpl implements Statistics {
    private static final Logger log = LoggerFactory.getLogger(StatisticsImpl.class);

    private final Supplier<ImmutableList<Upstream>> upstreams;
    private final LoadingCache<Endpoint, PerEndpointData> perEndpoint;
    /**
     * Computing the 'best' upstream for a given endpoint involves trawling through our statistics, which is a bit
     * computationally expensive, so when things are going well, we only do it at most once every 5 seconds. If two
     * nodes are performing well, this is the fastest we could switch to a better performing node.
     */
    private final LoadingCache<Endpoint, Optional<Upstream>> cachedBest;

    private final Randomness randomness;
    private final Ticker clock;
    private final Clock codahaleClock;

    StatisticsImpl(Supplier<ImmutableList<Upstream>> upstreams, Randomness randomness, Ticker clock) {
        // TODO(dfox): switch to a builder before these parameters get out of hand
        this.upstreams = upstreams;
        this.randomness = randomness;
        this.clock = clock;
        this.codahaleClock = clock == Ticker.systemTicker() ? Clock.defaultClock() : new CodahaleClock(clock);
        this.perEndpoint =
                Caffeine.newBuilder().maximumSize(1000).ticker(clock).build(endpoint -> new PerEndpointData());
        cachedBest = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(5))
                .ticker(clock)
                .build(this::computeBest);
    }

    interface Randomness {
        <T> Optional<T> selectRandom(List<T> list);
    }

    private ExponentiallyDecayingReservoir newReservoir() {
        // defaults copied from ExponentiallyDecayingReservoir
        int numberOfSamplesToStore = 1028;
        double exponentialDecayFactor = 0.015;

        // this class does in fact use a ThreadLocalRandom#nextDouble() to detemine whether a sample overwrites
        return new ExponentiallyDecayingReservoir(numberOfSamplesToStore, exponentialDecayFactor, codahaleClock);
    }

    @Override
    public InFlightStage recordStart(Upstream upstream, Endpoint endpoint, Request _request) {
        return new InFlightStage() {
            @Override
            public void recordComplete(Response response, Throwable throwable) {
                if (response != null) {

                    String version = response.getFirstHeader("server").orElse("unknown-version"); // opt?
                    int changeInConfidence = changeInConfidence(response);
                    perEndpoint.get(endpoint).get(upstream).update(version, changeInConfidence);

                    if (changeInConfidence < 0) {
                        // if we've had to penalize an upstream, we ensure that the next caller to this endpoint
                        // knows this has happened
                        cachedBest.invalidate(endpoint);
                    }

                } else if (throwable != null) {
                    // TODO(dfox): do we penalize upstreams for what is likely a client-side misconfiguration?
                }
            }
        };
    }

    private final class PerEndpointData {
        private final LoadingCache<Upstream, PerUpstreamData> perUpstream =
                Caffeine.newBuilder().maximumSize(100).ticker(clock).build(upstream -> new PerUpstreamData());

        @CheckReturnValue
        PerUpstreamData get(Upstream upstream) {
            return perUpstream.get(upstream);
        }
    }

    private final class PerUpstreamData {
        private volatile String lastSeenVersion;

        private final LoadingCache<String, Reservoir> perVersion =
                Caffeine.newBuilder().maximumSize(10).ticker(clock).build(version -> newReservoir());

        // TODO(dfox): include timing data in here too!

        void update(String version, long changeInConfidence) {
            Reservoir reservoir = perVersion.get(version);
            reservoir.update(changeInConfidence);
            lastSeenVersion = version;
        }
    }

    @SuppressWarnings("CyclomaticComplexity")
    private static int changeInConfidence(Response response) {
        switch (response.code()) {
            case 200:
            case 204:
                return 1;
            case 500:
            case 503:
            case 429:
                return -10;
            case 400:
            case 401:
            case 403:
            case 404:
            case 409: // conflict
            case 413: // too large
                return -1;
            default:
                return 0;
        }
    }

    // TODO(dfox): should this know about the history with retries etc
    Optional<Upstream> getBest(Endpoint endpoint) {
        return cachedBest.get(endpoint);
    }

    // TODO(dfox): introduce some jitter so that we explore other
    // TODO(dfox): this method iterates over possibly changing caches. can it not?
    @VisibleForTesting
    Optional<Upstream> computeBest(Endpoint endpoint) {
        Optional<Map.Entry<Upstream, Double>> max = perEndpoint.get(endpoint).perUpstream.asMap().entrySet().stream()
                .flatMap(entry -> {
                    Upstream upstream = entry.getKey();
                    PerUpstreamData perUpstreamData = entry.getValue();

                    @Nullable String version = perUpstreamData.lastSeenVersion;
                    Preconditions.checkNotNull(version, "TODO figure out logic here", SafeArg.of("v", version));
                    // maybe we just average the confidence for all the other versions? most recent version?

                    @Nullable Reservoir reservoir = perUpstreamData.perVersion.getIfPresent(version);
                    if (reservoir == null) {
                        log.info("[findbest] no data about upstream={}, version={}", upstream, version);
                        return Stream.empty();
                    }

                    double mean = reservoir.getSnapshot().getMean();
                    log.info("[findbest] Confidence={} for upstream={}, version={}", mean, upstream, version);
                    return Stream.of(Maps.immutableEntry(entry.getKey(), mean));
                })
                .max(Comparator.comparingDouble(e -> e.getValue()));

        if (max.isPresent()) {
            Map.Entry<Upstream, Double> bestSoFar = max.get();
            Double confidence = bestSoFar.getValue();
            if (confidence > 0) {
                return Optional.of(bestSoFar.getKey());
            } else {
                log.info("[selectBest] confidence is crap ({}), just picking first", confidence);
            }
        }

        // TODO(dfox): if we have negative confidence a node will be successful, avoid randomly selecting that one?
        return randomness.selectRandom(upstreams.get());
    }
}
