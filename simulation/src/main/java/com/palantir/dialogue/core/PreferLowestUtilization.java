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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PreferLowestUtilization implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PreferLowestUtilization.class);

    // integers are indexes into the 'channels' list
    private final LoadingCache<LimitedChannel, AtomicInteger> active =
            Caffeine.newBuilder().maximumSize(1000).build(upstream -> new AtomicInteger());
    private final ImmutableList<LimitedChannel> channels;
    private final Random random;

    PreferLowestUtilization(ImmutableList<LimitedChannel> channels, Random random) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.channels = channels;
        this.random = random;
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        if (channels.isEmpty()) {
            return Optional.empty();
        }

        // we accumulate everything right now (which is probably quite expensive), but it allows us to move on to the
        // next-best channel if our preferred one refuses
        Map<Integer, List<LimitedChannel>> channelsByActive = new TreeMap<>();
        for (LimitedChannel channel : shuffleImmutableList(channels, random)) {
            int activeRequests = active.get(channel).get();
            channelsByActive.compute(activeRequests, (key, existing) -> {
                if (existing == null) {
                    List<LimitedChannel> list = new ArrayList<>();
                    list.add(channel);
                    return list;
                } else {
                    existing.add(channel);
                    return existing;
                }
            });
        }

        log.debug(
                "traceid={} channelsByActive={}",
                request.headerParams().get("X-B3-TraceId"),
                channelsByActive.keySet());

        for (Integer activeCount : channelsByActive.keySet()) {
            List<LimitedChannel> candidates = channelsByActive.get(activeCount);
            for (LimitedChannel channel : candidates) {
                // log.info("time={} best={} active={}", Duration.ofNanos(clock.read()), channel, channelsByActive);

                AtomicInteger atomicInteger = active.get(channel);
                atomicInteger.incrementAndGet();

                Optional<ListenableFuture<Response>> maybeResponse = channel.maybeExecute(endpoint, request);
                if (maybeResponse.isPresent()) {
                    ListenableFuture<Response> response = maybeResponse.get();
                    response.addListener(atomicInteger::decrementAndGet, MoreExecutors.directExecutor());
                    return Optional.of(response);
                } else {
                    // we have to undo the atomicInteger thing we eagerly incremented.
                    atomicInteger.decrementAndGet();
                }
            }
        }

        log.debug("Every channel refused {}", channelsByActive);
        return Optional.empty();
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> ImmutableList<T> shuffleImmutableList(List<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return ImmutableList.copyOf(mutableList);
    }
}
