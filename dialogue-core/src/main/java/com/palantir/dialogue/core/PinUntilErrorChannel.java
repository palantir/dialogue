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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes all requests to one host until an error is received, then we'll move on to the next host.
 * Upside: ensures clients will always hit warm caches
 * Downsides:
 * <ul>
 *     <li>a single high volume client can result in unbalanced utilization of server nodes.
 *     <li>when a node is restarted (and clients see 500s/connect exceptions), they will _all_ pin to a different node
 * </ul>
 *
 * To alleviate the second downside, we reshuffle all nodes every 10 minutes.
 */
final class PinUntilErrorChannel implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PinUntilErrorChannel.class);

    // we also add some jitter to ensure that there isn't a big spike of reshuffling every 10 minutes.
    private static final Duration RESHUFFLE_EVERY = Duration.ofMinutes(10);

    // TODO(dfox): could we make this endpoint-specific?
    private final AtomicInteger currentHost = new AtomicInteger(0); // always positive
    private final NodeList nodeList;

    @VisibleForTesting
    PinUntilErrorChannel(NodeList nodeList) {
        this.nodeList = nodeList;
    }

    static PinUntilErrorChannel pinUntilError(List<LimitedChannel> channels) {
        Preconditions.checkArgument(!channels.isEmpty(), "List of channels must not be empty");
        ReshufflingNodeList shufflingNodeList =
                new ReshufflingNodeList(channels, SafeThreadLocalRandom.get(), System::nanoTime);
        return new PinUntilErrorChannel(shufflingNodeList);
    }

    /**
     * This strategy results in unbalanced server utilization, especially after an upstream restarts.
     * @deprecated prefer {@link #pinUntilError}
     */
    @Deprecated
    static PinUntilErrorChannel pinUntilErrorWithoutReshuffle(List<LimitedChannel> channels) {
        Preconditions.checkArgument(!channels.isEmpty(), "List of channels must not be empty");
        ConstantNodeList constantNodeList = new ConstantNodeList(channels, SafeThreadLocalRandom.get());
        return new PinUntilErrorChannel(constantNodeList);
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        int currentIndex = currentHost.get();
        LimitedChannel channel = nodeList.get(currentIndex);

        Optional<ListenableFuture<Response>> maybeFuture = channel.maybeExecute(endpoint, request);
        if (!maybeFuture.isPresent()) {
            OptionalInt next = markCurrentHostAsBad(currentIndex);
            debugLogCurrentChannelRejected(currentIndex, channel, next);
            return Optional.empty(); // if the caller retries immediately, we'll get the next host
        }

        ListenableFuture<Response> future = maybeFuture.get();
        return Optional.of(DialogueFutures.addDirectCallback(future, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response response) {
                if (response.code() >= 300) {
                    OptionalInt next = markCurrentHostAsBad(currentIndex);
                    debugLogReceivedErrorStatus(currentIndex, channel, response, next);
                    // TODO(dfox): handle 308 See Other somehow, as we currently don't have a host -> channel mapping
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                OptionalInt next = markCurrentHostAsBad(currentIndex);
                debugLogReceivedThrowable(currentIndex, channel, throwable, next);
            }
        }));
    }

    /**
     * If we have some reason to think the currentIndex is bad, we want to move to the next host. This is done with a
     * compareAndSet to ensure that out of order responses which signal information about a previous host don't kick
     * us off a good one.
     */
    private OptionalInt markCurrentHostAsBad(int currentIndex) {
        int nextIndex = Math.max(currentIndex + 1, 0); // we want Integer.MAX_VALUE to wrap around to zero

        boolean saved = currentHost.compareAndSet(currentIndex, nextIndex);
        return saved ? OptionalInt.of(nextIndex) : OptionalInt.empty(); // we've moved on already
    }

    interface NodeList {
        /**
         * Accepts positive indexes that are greater than the length of the list, returns an item modulo the length
         * of the list.
         */
        LimitedChannel get(int index);
    }

    @VisibleForTesting
    static final class ConstantNodeList implements NodeList {
        private final List<LimitedChannel> channels;

        ConstantNodeList(List<LimitedChannel> channels, Random random) {
            /**
             * Shuffle the initial list to ensure that clients across the fleet don't all traverse the list in the
             * same order.  If they did, then restarting one upstream node n would shift all its traffic (from all
             * servers) to upstream n+1. When n+1 restarts, it would all shift to n+2. This results in the disastrous
             * situation where there might be many nodes but all clients have decided to hammer one of them.
             */
            this.channels = shuffleImmutableList(channels, random);
        }

        @Override
        public LimitedChannel get(int index) {
            return channels.get(index % channels.size());
        }
    }

    @VisibleForTesting
    static final class ReshufflingNodeList implements NodeList {
        private final Ticker clock;
        private final Random random;
        private final long intervalWithJitter;
        private final int channelsSize;

        private final AtomicLong nextReshuffle;
        private volatile ImmutableList<LimitedChannel> channels;

        ReshufflingNodeList(List<LimitedChannel> channels, Random random, Ticker clock) {
            this.channels = shuffleImmutableList(channels, random);
            this.channelsSize = channels.size();
            this.random = random;
            this.intervalWithJitter = RESHUFFLE_EVERY
                    .plus(Duration.ofSeconds(random.nextInt(60) - 30))
                    .toNanos();
            this.nextReshuffle = new AtomicLong(clock.read() + intervalWithJitter);
            this.clock = clock;
        }

        @Override
        public LimitedChannel get(int index) {
            reshuffleChannelsIfNecessary();
            return channels.get(index % channelsSize);
        }

        private void reshuffleChannelsIfNecessary() {
            if (channels.size() <= 1) {
                return;
            }

            long reshuffleTime = nextReshuffle.get();
            if (clock.read() < reshuffleTime) {
                return;
            }

            if (nextReshuffle.compareAndSet(reshuffleTime, clock.read() + intervalWithJitter)) {
                ImmutableList<LimitedChannel> newList = shuffleImmutableList(channels, random);
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Reshuffling channels {} {}",
                            SafeArg.of("nextReshuffle", Duration.ofNanos(intervalWithJitter)),
                            UnsafeArg.of("newList", newList));
                }
                channels = newList;
            }
        }
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> ImmutableList<T> shuffleImmutableList(List<T> sourceList, Random random) {
        List<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return ImmutableList.copyOf(mutableList);
    }

    private void debugLogCurrentChannelRejected(int currentIndex, LimitedChannel channel, OptionalInt next) {
        if (log.isDebugEnabled()) {
            if (next.isPresent()) {
                log.debug(
                        "Current channel rejected request, switching to next channel",
                        SafeArg.of("currentIndex", currentIndex),
                        UnsafeArg.of("current", channel),
                        SafeArg.of("nextIndex", next.getAsInt()));
            } else {
                log.debug(
                        "Current channel rejected request, but we've already switched",
                        SafeArg.of("currentIndex", currentIndex),
                        UnsafeArg.of("current", channel));
            }
        }
    }

    private void debugLogReceivedErrorStatus(
            int currentIndex, LimitedChannel channel, Response response, OptionalInt next) {
        if (log.isDebugEnabled()) {
            if (next.isPresent()) {
                log.debug(
                        "Received error status code, switching to next channel",
                        SafeArg.of("status", response.code()),
                        SafeArg.of("currentIndex", currentIndex),
                        UnsafeArg.of("current", channel),
                        SafeArg.of("nextIndex", next.getAsInt()));
            } else {
                log.debug(
                        "Received error status code, but we've already switched",
                        SafeArg.of("status", response.code()),
                        SafeArg.of("currentIndex", currentIndex),
                        UnsafeArg.of("current", channel));
            }
        }
    }

    private void debugLogReceivedThrowable(
            int currentIndex, LimitedChannel channel, Throwable throwable, OptionalInt next) {
        if (log.isDebugEnabled()) {
            if (next.isPresent()) {
                log.debug(
                        "Received throwable, switching to next channel",
                        SafeArg.of("currentIndex", currentIndex),
                        UnsafeArg.of("current", channel),
                        SafeArg.of("nextIndex", next.getAsInt()),
                        throwable);
            } else {
                log.debug(
                        "Received throwable, but already switched",
                        SafeArg.of("currentIndex", currentIndex),
                        UnsafeArg.of("current", channel),
                        throwable);
            }
        }
    }
}
