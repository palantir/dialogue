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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The idea here is to keep track of how many requests are currently being served by each server, and when we're asked
 * to execute a request, just pick the one with the fewest in flight.
 *
 * This is intended to be a strict improvement over the {@link RandomSelectionChannel}, which can leave fast servers
 * underutilized, as it sends the same number to both a slow and fast node.
 *
 * I know this implementation is expensive to calculate - just intending to evaluate the usefulness of the
 * heuristic before any kind of optimizing e.g. power of two choices (P2C) to avoid a full sort.
 */
final class PreferLowestUtilization implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PreferLowestUtilization.class);

    /**
     * This comparator is a little risky because the data can change while we're sorting. In practise the sort
     * finishes a lot quicker than network requests do, and we don't mind if it's not exactly right.
     */
    private static final Comparator<Map.Entry<AtomicInteger, LimitedChannel>> PREFER_LOWEST =
            Comparator.comparing(pair -> pair.getKey().get());

    /** Each AtomicInteger stores the number of active requests that a channel is currently serving. */
    private final ImmutableList<Map.Entry<AtomicInteger, LimitedChannel>> channelsByActiveRequest;

    private final Random random;

    PreferLowestUtilization(ImmutableList<LimitedChannel> channels, Random random) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");

        this.random = random;
        this.channelsByActiveRequest = channels.stream()
                .map(c -> Maps.immutableEntry(new AtomicInteger(0), c))
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {

        // tie-breaking is pretty important here, otherwise when there are no requests in flight, we'd *always*
        // prefer the first channe of the list, leading to a higher overall load.
        Optional<ListenableFuture<Response>> response = shuffleImmutableList(channelsByActiveRequest, random).stream()
                .sorted(PREFER_LOWEST)
                .map(pair -> {
                    AtomicInteger atomicInteger = pair.getKey();
                    LimitedChannel channel = pair.getValue();

                    atomicInteger.incrementAndGet();
                    Optional<ListenableFuture<Response>> maybeFuture = channel.maybeExecute(endpoint, request);
                    if (!maybeFuture.isPresent()) {
                        atomicInteger.decrementAndGet(); // quick undo
                        return maybeFuture;
                    }

                    ListenableFuture<Response> future = maybeFuture.get();
                    future.addListener(atomicInteger::decrementAndGet, MoreExecutors.directExecutor());

                    return maybeFuture;
                })
                .filter(Optional::isPresent)
                // we rely heavily on streams being lazy here
                .findFirst()
                .map(Optional::get);

        if (log.isDebugEnabled() && !response.isPresent()) {
            List<Integer> utilization = channelsByActiveRequest.stream()
                    .map(entry -> entry.getKey().get())
                    .collect(Collectors.toList());
            log.debug("Every channel refused", SafeArg.of("utilization", utilization));
        }

        return response;
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }
}
