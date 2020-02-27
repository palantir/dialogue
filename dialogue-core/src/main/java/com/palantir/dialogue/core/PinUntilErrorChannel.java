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

import com.codahale.metrics.Meter;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
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
    private final AtomicInteger currentHost;
    private final NodeList nodeList;
    private final Instrumentation instrumentation;

    @VisibleForTesting
    PinUntilErrorChannel(NodeList nodeList, int initialHost, DialoguePinuntilerrorMetrics metrics) {
        this.nodeList = nodeList;
        this.currentHost = new AtomicInteger(initialHost);
        this.instrumentation = new Instrumentation(nodeList.size(), metrics);
        Preconditions.checkArgument(
                nodeList.size() >= 2,
                "PinUntilError is pointless if you have zero or 1 channels."
                        + " Use an always throwing channel or just pick the only channel in the list.");
    }

    static PinUntilErrorChannel of(
            NodeSelectionStrategy strategy,
            List<LimitedChannel> channels,
            DialoguePinuntilerrorMetrics metrics,
            Random random) {
        /**
         * The *initial* list is shuffled to ensure that clients across the fleet don't all traverse the in the
         * same order.  If they did, then restarting one upstream node n would shift all its traffic (from all
         * servers) to upstream n+1. When n+1 restarts, it would all shift to n+2. This results in the disastrous
         * situation where there might be many nodes but all clients have decided to hammer one of them.
         */
        ImmutableList<LimitedChannel> initialShuffle = shuffleImmutableList(channels, random);

        switch (strategy) {
            case PIN_UNTIL_ERROR:
                NodeList shuffling = ReshufflingNodeList.of(initialShuffle, random, System::nanoTime, metrics);
                return new PinUntilErrorChannel(shuffling, 0, metrics);
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                NodeList constant = new ConstantNodeList(initialShuffle);
                return new PinUntilErrorChannel(constant, 0, metrics);
            case ROUND_ROBIN:
        }

        throw new SafeIllegalArgumentException("Unsupported NodeSelectionStrategy", SafeArg.of("strategy", strategy));
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        return executeInternal(endpoint, request, 1);
    }

    private Optional<ListenableFuture<Response>> executeInternal(Endpoint endpoint, Request request, int depth) {
        int currentIndex = currentHost.get();
        LimitedChannel channel = nodeList.get(currentIndex);

        Optional<ListenableFuture<Response>> maybeFuture = channel.maybeExecute(endpoint, request);
        if (!maybeFuture.isPresent()) {
            OptionalInt next = incrementHostIfNecessary(currentIndex);
            instrumentation.currentChannelRejected(currentIndex, channel, next);
            // Try enough times to rotate through all nodes (assuming no concurrent clients) before returning
            // a completely rejected request.
            if (depth < nodeList.size()) {
                return executeInternal(endpoint, request, depth + 1);
            }
            return Optional.empty();
        }

        ListenableFuture<Response> future = maybeFuture.get();
        return Optional.of(DialogueFutures.addDirectCallback(future, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response response) {
                if (Responses.isQosStatus(response) || Responses.isServerError(response)) {
                    OptionalInt next = incrementHostIfNecessary(currentIndex);
                    instrumentation.receivedErrorStatus(currentIndex, channel, response, next);
                    // TODO(dfox): handle 308 See Other somehow, as we currently don't have a host -> channel mapping
                } else {
                    instrumentation.successfulResponse(currentIndex);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                OptionalInt next = incrementHostIfNecessary(currentIndex);
                instrumentation.receivedThrowable(currentIndex, channel, throwable, next);
            }
        }));
    }

    /**
     * If we have some reason to think the currentIndex is bad, we want to move to the next host. This is done with a
     * compareAndSet to ensure that out of order responses which signal information about a previous host don't kick
     * us off a good one.
     */
    private OptionalInt incrementHostIfNecessary(int currentIndex) {
        int nextIndex = (currentIndex + 1) % nodeList.size();
        boolean saved = currentHost.compareAndSet(currentIndex, nextIndex);
        return saved ? OptionalInt.of(nextIndex) : OptionalInt.empty(); // we've moved on already
    }

    interface NodeList {
        LimitedChannel get(int index);

        int size();
    }

    @VisibleForTesting
    static final class ConstantNodeList implements NodeList {
        private final List<LimitedChannel> channels;

        ConstantNodeList(List<LimitedChannel> channels) {
            this.channels = channels;
        }

        @Override
        public LimitedChannel get(int index) {
            return channels.get(index);
        }

        @Override
        public int size() {
            return channels.size();
        }
    }

    @VisibleForTesting
    static final class ReshufflingNodeList implements NodeList {
        private final Ticker clock;
        private final Random random;
        private final long intervalWithJitter;
        private final int channelsSize;
        private final Instrumentation instrumentation;

        private final AtomicLong nextReshuffle;
        private volatile ImmutableList<LimitedChannel> channels;

        static ReshufflingNodeList of(
                ImmutableList<LimitedChannel> channels,
                Random random,
                Ticker clock,
                DialoguePinuntilerrorMetrics metrics) {
            long intervalWithJitter = RESHUFFLE_EVERY
                    .plus(Duration.ofSeconds(random.nextInt(60) - 30))
                    .toNanos();
            AtomicLong nextReshuffle = new AtomicLong(clock.read() + intervalWithJitter);
            return new ReshufflingNodeList(channels, random, clock, metrics, intervalWithJitter, nextReshuffle);
        }

        private ReshufflingNodeList(
                ImmutableList<LimitedChannel> channels,
                Random random,
                Ticker clock,
                DialoguePinuntilerrorMetrics metrics,
                long intervalWithJitter,
                AtomicLong nextReshuffle) {
            this.channels = channels;
            this.channelsSize = channels.size();
            this.nextReshuffle = nextReshuffle;
            this.intervalWithJitter = intervalWithJitter;
            this.random = random;
            this.clock = clock;
            this.instrumentation = new Instrumentation(channelsSize, metrics);
        }

        @Override
        public LimitedChannel get(int index) {
            reshuffleChannelsIfNecessary();
            return channels.get(index);
        }

        @Override
        public int size() {
            return channelsSize;
        }

        private void reshuffleChannelsIfNecessary() {
            long reshuffleTime = nextReshuffle.get();
            if (clock.read() < reshuffleTime) {
                return;
            }

            if (nextReshuffle.compareAndSet(reshuffleTime, clock.read() + intervalWithJitter)) {
                ImmutableList<LimitedChannel> newList = shuffleImmutableList(channels, random);
                instrumentation.reshuffled(newList, intervalWithJitter);
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

    /** Purely for metric and service log observability. */
    private static final class Instrumentation {
        @Nullable
        private final Meter[] successesPerHost;

        private final Meter reshuffleMeter;
        private final Meter nextNodeBecauseLimited;
        private final Meter nextNodeBecauseResponseCode;
        private final Meter nextNodeBecauseThrowable;

        Instrumentation(int numChannels, DialoguePinuntilerrorMetrics metrics) {
            this.reshuffleMeter = metrics.reshuffle();
            this.nextNodeBecauseLimited = metrics.nextNode("limited");
            this.nextNodeBecauseResponseCode = metrics.nextNode("responseCode");
            this.nextNodeBecauseThrowable = metrics.nextNode("throwable");

            if (numChannels < 10) {
                // hard limit ensures we don't create unbounded tags
                this.successesPerHost = IntStream.range(0, numChannels)
                        .mapToObj(index -> metrics.success(Integer.toString(index)))
                        .toArray(Meter[]::new);
            } else {
                this.successesPerHost = null;
            }
        }

        private void reshuffled(ImmutableList<LimitedChannel> newList, long intervalWithJitter) {
            reshuffleMeter.mark();
            if (log.isDebugEnabled()) {
                log.debug(
                        "Reshuffled channels {} {}",
                        SafeArg.of("nextReshuffle", Duration.ofNanos(intervalWithJitter)),
                        UnsafeArg.of("newList", newList));
            }
        }

        private void currentChannelRejected(int currentIndex, LimitedChannel channel, OptionalInt next) {
            if (next.isPresent()) {
                nextNodeBecauseLimited.mark();
            }
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

        private void receivedErrorStatus(
                int currentIndex, LimitedChannel channel, Response response, OptionalInt next) {
            if (next.isPresent()) {
                nextNodeBecauseResponseCode.mark();
            }
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

        private void receivedThrowable(
                int currentIndex, LimitedChannel channel, Throwable throwable, OptionalInt next) {
            if (next.isPresent()) {
                nextNodeBecauseThrowable.mark();
            }
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

        private void successfulResponse(int currentIndex) {
            if (successesPerHost != null) {
                successesPerHost[currentIndex].mark();
            }
        }
    }
}
