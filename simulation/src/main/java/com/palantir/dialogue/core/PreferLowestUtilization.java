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
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PreferLowestUtilization implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PreferLowestUtilization.class);

    // integers are indexes into the 'channels' list
    private final LoadingCache<LimitedChannel, AtomicInteger> active =
            Caffeine.newBuilder().maximumSize(1000).build(upstream -> new AtomicInteger());
    private final ImmutableList<LimitedChannel> channels;
    private final Ticker clock;
    private final Randomness randomness;

    public PreferLowestUtilization(ImmutableList<LimitedChannel> channels, Ticker clock, Randomness randomness) {
        this.channels = channels;
        this.clock = clock;
        this.randomness = randomness;
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        if (channels.isEmpty()) {
            return Optional.empty();
        }

        // we accumulate everything right now (which is probably quite expensive), but it allows us to move on to the
        // next-best channel if our preferred one refuses
        Map<Integer, List<LimitedChannel>> channelsByActive = new TreeMap<>();
        for (LimitedChannel channel : channels) {
            int activeRequests = active.get(channel).get();
            channelsByActive.compute(activeRequests, (key, existing) -> {
                if (existing == null) {
                    ArrayList<LimitedChannel> list = new ArrayList<>();
                    list.add(channel);
                    return list;
                } else {
                    existing.add(channel);
                    return existing;
                }
            });
        }

        // this relies on the cache being pre-filled (containing some channel -> 0 mappings).
        for (Integer activeCount : channelsByActive.keySet()) {
            List<LimitedChannel> candidates = channelsByActive.get(activeCount);
            // List<LimitedChannel> tiebroken = randomness.shuffle(candidates);
            for (LimitedChannel channel : candidates) {
                log.debug("time={} best={} active={}", Duration.ofNanos(clock.read()), channel, channelsByActive);

                AtomicInteger atomicInteger = active.get(channel);
                atomicInteger.incrementAndGet();

                Optional<ListenableFuture<Response>> maybeResponse = channel.maybeExecute(endpoint, request);
                if (maybeResponse.isPresent()) {
                    ListenableFuture<Response> response = maybeResponse.get();
                    response.addListener(atomicInteger::decrementAndGet, MoreExecutors.directExecutor());
                    return Optional.of(response);
                }

                // we have to undo the atomicInteger thing we eagerly incremented.
                atomicInteger.decrementAndGet();
            }
        }

        log.info("Every single channel refused :( {}", channelsByActive);
        return Optional.empty();
    }

    private static <T> ImmutableList<T> merge(List<T> left, List<T> right) {
        return ImmutableList.<T>builder().addAll(left).addAll(right).build();
    }
}
