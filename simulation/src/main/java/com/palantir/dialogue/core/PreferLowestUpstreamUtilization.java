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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PreferLowestUpstreamUtilization implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PreferLowestUpstreamUtilization.class);

    private final LoadingCache<Integer, AtomicInteger> active =
            Caffeine.newBuilder().maximumSize(1000).build(upstream -> new AtomicInteger());
    private final ImmutableList<Channel> upstreams;
    private final Ticker clock;

    public PreferLowestUpstreamUtilization(ImmutableList<Channel> upstreams, Ticker clock) {
        this.upstreams = upstreams;
        this.clock = clock;
    }

    public Statistics.InFlightStage recordStart(Integer upstream, Endpoint _endpoint, Request _request) {
        AtomicInteger atomicInteger = active.get(upstream);
        atomicInteger.incrementAndGet();
        return new Statistics.InFlightStage() {
            @Override
            public void recordComplete(@Nullable Response _response, @Nullable Throwable _throwable) {
                atomicInteger.decrementAndGet();
            }
        };
    }

    public Optional<Channel> getBest(Endpoint _endpoint) {
        if (upstreams.isEmpty()) {
            return Optional.empty();
        }

        int lowest = Integer.MAX_VALUE;
        List<Channel> best = new ArrayList<>(); // store multiple so we can tiebreak
        for (int i = 0; i < upstreams.size(); i++) {
            int currentActiveRequests = active.get(i).get();

            if (currentActiveRequests < lowest) {
                lowest = currentActiveRequests;
                best.clear();
                best.add(upstreams.get(i));
            } else if (currentActiveRequests == lowest) {
                best.add(upstreams.get(i));
            }
        }

        // TODO(dfox): tiebreaking currently always picks the first upstream when they have the same utilization
        Channel bestChannel = best.get(0);
        log.info("time={} best={} active={}", Duration.ofNanos(clock.read()), best, active.asMap());
        return Optional.of(bestChannel);
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        Optional<Channel> best = getBest(endpoint);
        if (!best.isPresent()) {
            return Optional.empty();
        }

        Channel delegate = best.get();
        ListenableFuture<Response> response = delegate.execute(endpoint, request);
        return Optional.of(response);
    }
}
