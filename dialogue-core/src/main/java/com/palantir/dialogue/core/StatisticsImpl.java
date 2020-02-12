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

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * heuristic: probability of success in a <endpoint,upstream,version> combo increases slowly,
 * - successful responses (200s) increase confidence by 1
 * - failures (500s) decrease confidence by 10
 * - client side exceptions decrease confidence by 20
 * - confidence decays over time
 *
 * TODO(dfox): put in ticker to make all this stuff deterministic
 *
 * data layout is "endpoint -> upstream -> version -> confidence"
 *
 * we want to query by endpoint and find the upstream with the best score
 */
final class StatisticsImpl implements Statistics {

    private volatile ImmutableList<Upstream> upstreams = ImmutableList.of();

    private final LoadingCache<Endpoint, PerEndpointData> perEndpoint =
            Caffeine.newBuilder().maximumSize(1000).build(endpoint -> new PerEndpointData());

    public void updateUpstreams(ImmutableList<Upstream> value) {
        this.upstreams = value;
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

                } else if (throwable != null) {
                    // TODO(dfox): do we penalize upstreams for what is likely a client-side misconfiguration?
                }
            }
        };
    }

    private static class PerEndpointData {
        private final LoadingCache<Upstream, PerUpstreamData> perUpstream =
                Caffeine.newBuilder().maximumSize(100).build(upstream -> new PerUpstreamData());

        @CheckReturnValue
        PerUpstreamData get(Upstream upstream) {
            return perUpstream.get(upstream);
        }
    }

    private static class PerUpstreamData {
        private volatile String lastSeenVersion;

        private final LoadingCache<String, ExponentiallyDecayingReservoir> perVersion =
                Caffeine.newBuilder().maximumSize(10).build(version -> new ExponentiallyDecayingReservoir());

        // TODO(dfox): include timing data in here too!

        void update(String version, long changeInConfidence) {
            ExponentiallyDecayingReservoir reservoir = perVersion.get(version);
            reservoir.update(changeInConfidence);
            lastSeenVersion = version;
        }
    }

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

    // TODO(dfox): cache this method until there's a failure on an endpoint
    // TODO(dfox): introduce some jitter so that we explore other
    // TODO(dfox): this method iterates over possibly changing caches. can it not?
    public Optional<Upstream> selectBestUpstreamFor(Endpoint endpoint) {
        Optional<Map.Entry<Upstream, Double>> max = perEndpoint.get(endpoint).perUpstream.asMap().entrySet().stream()
                .flatMap(entry -> {
                    Upstream upstream = entry.getKey();
                    PerUpstreamData perUpstreamData = entry.getValue();

                    @Nullable String version = perUpstreamData.lastSeenVersion;
                    Preconditions.checkNotNull(version, "TODO figure out logic here", SafeArg.of("v", version));
                    // maybe we just average the confidence for all the other versions? most recent version?

                    @Nullable
                    ExponentiallyDecayingReservoir reservoir = perUpstreamData.perVersion.getIfPresent(version);
                    if (reservoir == null) {
                        System.out.println("[findbest] no data about upstream & version " + upstream + ", " + version);
                        return Stream.empty();
                    }

                    double mean = reservoir.getSnapshot().getMean();
                    System.out.println("[findbest] Confidence for " + upstream + ", " + version + " " + mean);
                    return Stream.of(Maps.immutableEntry(entry.getKey(), mean));
                })
                .max(Comparator.comparingDouble(e -> e.getValue()));

        if (max.isPresent()) {
            Map.Entry<Upstream, Double> bestSoFar = max.get();
            Double confidence = bestSoFar.getValue();
            if (confidence > 0) {
                return Optional.of(bestSoFar.getKey());
            } else {
                System.out.println("[selectBest] confidence is crap, just picking first " + confidence);
            }
        }

        return upstreams.stream().findFirst();
    }
}
