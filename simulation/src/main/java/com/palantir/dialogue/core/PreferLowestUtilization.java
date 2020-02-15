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

public final class PreferLowestUtilization implements LimitedChannel, Statistics {
    private static final Logger log = LoggerFactory.getLogger(PreferLowestUtilization.class);

    // integers are indexes into the 'channels' list
    private final LoadingCache<Channel, AtomicInteger> active =
            Caffeine.newBuilder().maximumSize(1000).build(upstream -> new AtomicInteger());
    private final ImmutableList<Channel> channels;
    private final Ticker clock;

    public PreferLowestUtilization(ImmutableList<Channel> channels, Ticker clock) {
        this.channels = channels;
        this.clock = clock;
    }

    @Override
    public Statistics.InFlightStage recordStart(Channel upstream, Endpoint _endpoint, Request _request) {
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
        if (channels.isEmpty()) {
            return Optional.empty();
        }

        int lowest = Integer.MAX_VALUE;
        List<Channel> best = new ArrayList<>(); // store multiple so we can tiebreak
        for (Channel channel : channels) {
            int currentActiveRequests = active.get(channel).get();

            if (currentActiveRequests < lowest) {
                lowest = currentActiveRequests;
                best.clear();
                best.add(channel);
            } else if (currentActiveRequests == lowest) {
                best.add(channel);
            }
        }

        // TODO(dfox): tiebreaking currently always picks the first upstream when they have the same utilization
        Channel bestChannel = best.get(0);
        log.debug("time={} best={} active={}", Duration.ofNanos(clock.read()), best, active.asMap());
        return Optional.of(bestChannel);
    }
}
