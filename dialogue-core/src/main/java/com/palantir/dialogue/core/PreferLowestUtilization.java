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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.immutables.value.Value;
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

    private final ImmutableList<ChannelWithStats> channels;
    private final Random random;

    PreferLowestUtilization(ImmutableList<LimitedChannel> channels, Random random) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.random = random;
        this.channels = channels.stream().map(ChannelWithStats::of).collect(ImmutableList.toImmutableList());
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        // pre-shuffling to tie-break is pretty important here, otherwise when there are no requests in flight, we'd
        // *always* prefer the first channel of the list, leading to a higher overall load.
        return shuffleImmutableList(channels, random).stream()
                .sorted(PREFER_LOWEST_TIEBREAK)
                .map(channel -> channel.maybeExecute(endpoint, request))
                .filter(Optional::isPresent)
                .map(Optional::get)
                // we rely heavily on streams being lazy here
                .findFirst();
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> List<T> shuffleImmutableList(ImmutableList<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return mutableList;
    }

    /**
     * This comparator is a little risky because the data can change while we're sorting. In practise the sort
     * finishes a lot quicker than network requests do, and we don't mind if it's not exactly right.
     */
    @VisibleForTesting
    static final Comparator<ChannelWithStats> PREFER_LOWEST_TIEBREAK = Comparator.comparing((ChannelWithStats c) -> {
                return c.inflight().get();
            })
            .thenComparing((ChannelWithStats c) -> {
                return c.lastRequestFailed().get();
            });

    @Value.Immutable
    interface ChannelWithStats extends LimitedChannel {

        LimitedChannel delegate();

        /** Number of requests which are being sent down this channel. */
        AtomicInteger inflight();

        /** Trivial bit of memory for tie-breaking. */
        AtomicBoolean lastRequestFailed();

        static ChannelWithStats of(LimitedChannel channel) {
            return ImmutableChannelWithStats.builder()
                    .delegate(channel)
                    .inflight(new AtomicInteger(0))
                    .lastRequestFailed(new AtomicBoolean(false))
                    .build();
        }

        @Override
        default Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
            int currentInflight = inflight().incrementAndGet();
            Optional<ListenableFuture<Response>> maybe = delegate().maybeExecute(endpoint, request);

            if (maybe.isPresent()) {
                // if (log.isInfoEnabled()) {
                    log.info("channel {} inflight {}", delegate(), currentInflight);
                // }

                DialogueFutures.addDirectCallback(maybe.get(), new FutureCallback<Response>() {
                    @Override
                    public void onSuccess(Response result) {
                        inflight().decrementAndGet();
                        lastRequestFailed().set(Responses.isQosStatus(result) || Responses.isServerError(result));
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        lastRequestFailed().set(true);
                    }
                });
            } else {
                inflight().decrementAndGet(); // quickly undo
            }
            return maybe;
        }
    }
}
