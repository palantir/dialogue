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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
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
 * TODO(dfox): ensure we give new versions *some* traffic (they might be better!)
 * TODO(dfox): put in ticker to make all this stuff deterministic
 *
 * we want to query by endpoint and find the upstream with the best score
 */
final class StatisticsImpl implements Statistics {

    private volatile ImmutableList<Upstream> upstreams = ImmutableList.of();
    private final LoadingCache<Endpoint, MutableRecs> mutableData =
            Caffeine.newBuilder().maximumSize(1000).build(endpoint -> new MutableRecs());

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
                    mutableData.get(endpoint).update(upstream, version, changeInConfidence);

                } else if (throwable != null) {
                    // TODO(dfox): do we penalize upstreams for what is likely a client-side misconfiguration?
                }
            }
        };
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

    public Optional<Upstream> selectBestUpstreamFor(Endpoint endpoint) {
        Optional<Map.Entry<Upstream, Double>> maybeKnownBest = mutableData.get(endpoint).scoreUpstreams();

        if (maybeKnownBest.isPresent()) {
            Map.Entry<Upstream, Double> bestSoFar = maybeKnownBest.get();
            Double confidence = bestSoFar.getValue();
            if (confidence > 0) {
                return Optional.of(bestSoFar.getKey());
            } else {
                System.out.println("[selectBest] confidence is crap, just picking first " + confidence);
            }
        }

        return upstreams.stream().findFirst();
    }

    private static class MutableRecs {
        private final LoadingCache<Upstream, LoadingCache<String, ExponentiallyDecayingReservoir>> confidence =
                Caffeine.newBuilder().maximumSize(100).build(upstream ->
                        Caffeine.newBuilder().maximumSize(10).build(version -> new ExponentiallyDecayingReservoir()));

        private final Cache<Upstream, String> currentVersion =
                Caffeine.newBuilder().maximumSize(100).build();

        void update(Upstream upstream, String version, long changeInConfidence) {
            currentVersion.put(upstream, version);
            confidence.get(upstream).get(version).update(changeInConfidence);
            System.out.println(
                    "[update] updated confidence for " + upstream + ", " + version + " " + changeInConfidence);
        }

        public Optional<Map.Entry<Upstream, Double>> scoreUpstreams() {
            Optional<Map.Entry<Upstream, Double>> max = confidence.asMap().entrySet().stream()
                    .flatMap(entry -> {
                        Upstream upstream = entry.getKey();
                        LoadingCache<String, ExponentiallyDecayingReservoir> reservoirs = entry.getValue();

                        @Nullable String version = currentVersion.getIfPresent(upstream);
                        Preconditions.checkNotNull(version, "TODO figure out logic here", SafeArg.of("v", version));
                        // maybe we just average the confidence for all the other versions? most recent version?

                        @Nullable ExponentiallyDecayingReservoir reservoir = reservoirs.getIfPresent(version);
                        if (reservoir == null) {
                            System.out.println(
                                    "[findbest] no data about upstream & version " + upstream + ", " + version);
                            return Stream.empty();
                        }

                        double mean = reservoir.getSnapshot().getMean();
                        System.out.println("[findbest] Confidence for " + upstream + ", " + version + " " + mean);
                        return Stream.of(Maps.immutableEntry(entry.getKey(), mean));
                    })
                    .max(Comparator.comparingDouble(e -> e.getValue()));
            System.out.println("[findbest] chose " + max);
            return max;
        }
    }
}
