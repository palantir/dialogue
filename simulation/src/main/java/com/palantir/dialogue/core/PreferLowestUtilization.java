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
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The idea here is to keep track of how many requests are currently being served by each server, and when we're asked
 * to execute a request, just pick the one with the fewest in flight.
 *
 * This is intended to be a strict improvement over the {@link RoundRobinChannel}, which can leave fast servers
 * underutilized, as it sends the same number to both a slow and fast node.
 *
 * I know this implementation is expensive to calculate - just intending to evaluate the usefulness of the
 * heuristic before any kind of optimizing e.g. power of two choices (P2C) to avoid a full sort.
 */
final class PreferLowestUtilization implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PreferLowestUtilization.class);

    private final Random random;
    private final ImmutableList<LimitedChannel> channels;

    /** Each AtomicInteger stores the number of active requests that a channel is currently serving. */
    private final LoadingCache<LimitedChannel, AtomicInteger> activeRequestsPerChannel =
            Caffeine.newBuilder().maximumSize(1000).build(upstream -> new AtomicInteger());

    PreferLowestUtilization(ImmutableList<LimitedChannel> channels, Random random) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.channels = channels;
        this.random = random;
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {

        // we accumulate everything right now (which is quite expensive), but it allows us to move on to the
        // next-best channel if our preferred one refuses
        ListMultimap<Integer, LimitedChannel> channelsByUtilization = sortChannelsByLowestUtilization();

        for (Integer activeCount : channelsByUtilization.keySet()) {
            // multiple channels might have the same number of inflight requests
            for (LimitedChannel channel : channelsByUtilization.get(activeCount)) {

                AtomicInteger atomicInteger = activeRequestsPerChannel.get(channel);
                atomicInteger.incrementAndGet();

                Optional<ListenableFuture<Response>> maybeResponse = channel.maybeExecute(endpoint, request);

                if (maybeResponse.isPresent()) {
                    ListenableFuture<Response> response = maybeResponse.get();
                    response.addListener(atomicInteger::decrementAndGet, MoreExecutors.directExecutor());
                    return Optional.of(response);
                } else {
                    // we quickly undo the atomicInteger thing we optimistically incremented earlier
                    atomicInteger.decrementAndGet();
                }
            }
        }

        log.debug("Every channel refused {}", channelsByUtilization);
        return Optional.empty();
    }

    private ListMultimap<Integer, LimitedChannel> sortChannelsByLowestUtilization() {
        ListMultimap<Integer, LimitedChannel> channelsByUtilization =
                MultimapBuilder.treeKeys().arrayListValues().build();

        // the shuffle ensures that when nodes have the same utilization, we won't always pick the same one
        for (LimitedChannel channel : shuffleImmutableList(channels)) {
            int activeRequests = activeRequestsPerChannel.get(channel).get();
            channelsByUtilization.put(activeRequests, channel);
        }

        return channelsByUtilization;
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }
}
