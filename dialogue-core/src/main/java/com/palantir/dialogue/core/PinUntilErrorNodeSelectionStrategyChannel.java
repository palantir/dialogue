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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.time.Duration;
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
 * Routes all requests to one host until an error is received, then we'll move on to the next host. We explicitly
 * do not consider limited responses as errors when determining whether to change hosts, trading off improved locality
 * over overall throughput.
 * Upside: ensures clients will always hit warm caches
 * Downsides:
 * <ul>
 *     <li>a single high volume client can result in unbalanced utilization of server nodes.
 *     <li>when a node is restarted (and clients see 500s/connect exceptions), they will _all_ pin to a different node
 * </ul>
 *
 * To alleviate the second downside, we reshuffle all nodes every 10 minutes.
 */
final class PinUntilErrorNodeSelectionStrategyChannel implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PinUntilErrorNodeSelectionStrategyChannel.class);

    // we also add some jitter to ensure that there isn't a big spike of reshuffling every 10 minutes.
    private static final Duration RESHUFFLE_EVERY = Duration.ofMinutes(10);

    private final AtomicInteger currentPin;
    private final NodeList nodeList;
    private final Instrumentation instrumentation;

    @VisibleForTesting
    PinUntilErrorNodeSelectionStrategyChannel(
            NodeList nodeList, int initialPin, DialoguePinuntilerrorMetrics metrics, String channelName) {
        this.nodeList = nodeList;
        this.currentPin = new AtomicInteger(initialPin);
        this.instrumentation = new Instrumentation(nodeList.size(), metrics, channelName);
        Preconditions.checkArgument(
                nodeList.size() >= 2,
                "PinUntilError is pointless if you have zero or 1 channels."
                        + " Use an always throwing channel or just pick the only channel in the list.");
        Preconditions.checkArgument(
                0 <= initialPin && initialPin < nodeList.size(),
                "initialHost must be a valid index into nodeList",
                SafeArg.of("initialHost", initialPin));
    }

    static PinUntilErrorNodeSelectionStrategyChannel of(
            Optional<PinUntilErrorNodeSelectionStrategyChannel> previous,
            DialogueNodeSelectionStrategy strategy,
            HostAndLimitedChannels channels,
            DialoguePinuntilerrorMetrics metrics,
            Random random,
            Ticker ticker,
            String channelName) {

        /*
         * The *initial* list is shuffled to ensure that clients across the fleet don't all traverse the in the
         * same order.  If they did, then restarting one upstream node n would shift all its traffic (from all
         * servers) to upstream n+1. When n+1 restarts, it would all shift to n+2. This results in the disastrous
         * situation where there might be many nodes but all clients have decided to hammer one of them.
         */
        HostAndLimitedChannels initialShuffle = channels.shuffle(random);
        int initialPin = previous
                // indexOf relies on reference equality since we expect LimitedChannels to be reused across updates
                .map(pinUntilErrorNodeSelectionStrategyChannel -> Math.max(
                        0,
                        initialShuffle
                                .getChannels()
                                .indexOf(pinUntilErrorNodeSelectionStrategyChannel.getCurrentChannel())))
                .orElse(0);

        if (strategy == DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR) {
            NodeList shuffling = ReshufflingNodeList.of(initialShuffle, random, ticker, metrics, channelName);
            return new PinUntilErrorNodeSelectionStrategyChannel(shuffling, initialPin, metrics, channelName);
        } else if (strategy == DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE) {
            NodeList constant = new ConstantNodeList(initialShuffle);
            return new PinUntilErrorNodeSelectionStrategyChannel(constant, initialPin, metrics, channelName);
        }

        throw new SafeIllegalArgumentException("Unsupported NodeSelectionStrategy", SafeArg.of("strategy", strategy));
    }

    @VisibleForTesting
    HostAndLimitedChannel getCurrentChannel() {
        return nodeList.get(currentPin.get());
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        int pin = currentPin.get();
        HostAndLimitedChannel channel = nodeList.get(pin);

        Optional<ListenableFuture<Response>> maybeResponse =
                channel.limitedChannel().maybeExecute(endpoint, request, limitEnforcement);
        if (!maybeResponse.isPresent()) {
            return Optional.empty();
        }

        DialogueFutures.addDirectCallback(maybeResponse.get(), new FutureCallback<>() {
            @Override
            public void onSuccess(Response response) {
                // We specifically don't switch  429 responses to support transactional
                // workflows where it is important for a large number of requests to all land on the same node,
                // even if a couple of them get rate limited in the middle.
                if (Responses.isServerErrorRange(response)
                        || (Responses.isQosStatus(response) && !Responses.isTooManyRequests(response))) {
                    OptionalInt next = incrementHostIfNecessary(pin);
                    instrumentation.receivedErrorStatus(pin, channel, response, next);
                } else {
                    instrumentation.successfulResponse(channel.getHostIdx());
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                OptionalInt next = incrementHostIfNecessary(pin);
                instrumentation.receivedThrowable(pin, channel, throwable, next);
            }
        });
        return maybeResponse;
    }

    @VisibleForTesting
    HostAndLimitedChannels getNodeListSnapshot() {
        return nodeList.getSnapshot();
    }

    /**
     * If we have some reason to think the currentIndex is bad, we want to move to the next host. This is done with a
     * compareAndSet to ensure that out of order responses which signal information about a previous host don't kick
     * us off a good one.
     */
    private OptionalInt incrementHostIfNecessary(int pin) {
        int nextIndex = (pin + 1) % nodeList.size();
        boolean saved = currentPin.compareAndSet(pin, nextIndex);
        return saved ? OptionalInt.of(nextIndex) : OptionalInt.empty(); // we've moved on already
    }

    interface NodeList {
        HostAndLimitedChannel get(int index);

        int size();

        HostAndLimitedChannels getSnapshot();
    }

    @VisibleForTesting
    static final class ConstantNodeList implements NodeList {
        private final HostAndLimitedChannels channels;

        ConstantNodeList(HostAndLimitedChannels channels) {
            this.channels = channels;
        }

        @Override
        public HostAndLimitedChannel get(int index) {
            return channels.getChannels().get(index);
        }

        @Override
        public int size() {
            return channels.getChannels().size();
        }

        @Override
        public HostAndLimitedChannels getSnapshot() {
            return channels;
        }

        @Override
        public String toString() {
            return "ConstantNodeList{" + channels + '}';
        }
    }

    @VisibleForTesting
    static final class ReshufflingNodeList implements NodeList {
        private final Ticker clock;
        private final Random random;
        private final long intervalWithJitter;
        private final PinUntilErrorNodeSelectionStrategyChannel.Instrumentation instrumentation;

        private final AtomicLong nextReshuffle;
        private volatile HostAndLimitedChannels channels;

        static ReshufflingNodeList of(
                HostAndLimitedChannels channels,
                Random random,
                Ticker clock,
                DialoguePinuntilerrorMetrics metrics,
                String channelName) {
            long intervalWithJitter = RESHUFFLE_EVERY
                    .plus(Duration.ofSeconds(random.nextInt(60) - 30))
                    .toNanos();
            AtomicLong nextReshuffle = new AtomicLong(clock.read() + intervalWithJitter);
            return new ReshufflingNodeList(
                    channels, random, clock, metrics, intervalWithJitter, nextReshuffle, channelName);
        }

        private ReshufflingNodeList(
                HostAndLimitedChannels channels,
                Random random,
                Ticker clock,
                DialoguePinuntilerrorMetrics metrics,
                long intervalWithJitter,
                AtomicLong nextReshuffle,
                String channelName) {
            this.channels = channels;
            this.nextReshuffle = nextReshuffle;
            this.intervalWithJitter = intervalWithJitter;
            this.random = random;
            this.clock = clock;
            this.instrumentation = new Instrumentation(channels.getChannels().size(), metrics, channelName);
        }

        @Override
        public HostAndLimitedChannel get(int index) {
            reshuffleChannelsIfNecessary();
            return channels.getChannels().get(index);
        }

        @Override
        public HostAndLimitedChannels getSnapshot() {
            return channels;
        }

        @Override
        public int size() {
            return channels.getChannels().size();
        }

        private void reshuffleChannelsIfNecessary() {
            long reshuffleTime = nextReshuffle.get();
            if (clock.read() < reshuffleTime) {
                return;
            }

            if (nextReshuffle.compareAndSet(reshuffleTime, clock.read() + intervalWithJitter)) {
                HostAndLimitedChannels newList = channels.shuffle(random);
                instrumentation.reshuffled(newList.getChannels(), intervalWithJitter);
                channels = newList;
            }
        }

        @Override
        public String toString() {
            return "ReshufflingNodeList{channels="
                    + channels + ", nextReshuffle="
                    + nextReshuffle + ", intervalWithJitter="
                    + intervalWithJitter + '}';
        }
    }

    /** Purely for metric and service log observability. */
    private static final class Instrumentation {
        @Nullable
        private final Meter[] successesPerHost;

        private final Meter reshuffleMeter;
        private final Meter nextNodeBecauseResponseCode;
        private final Meter nextNodeBecauseThrowable;
        private final String channelName;
        private final int numChannels;

        Instrumentation(int numChannels, DialoguePinuntilerrorMetrics metrics, String channelName) {
            this.numChannels = numChannels;
            this.channelName = channelName;
            this.reshuffleMeter = metrics.reshuffle(channelName);
            this.nextNodeBecauseResponseCode = metrics.nextNode()
                    .channelName(channelName)
                    .reason("responseCode")
                    .build();
            this.nextNodeBecauseThrowable = metrics.nextNode()
                    .channelName(channelName)
                    .reason("throwable")
                    .build();

            if (numChannels < 10) {
                // hard limit ensures we don't create unbounded tags
                this.successesPerHost = IntStream.range(0, numChannels)
                        .mapToObj(index -> metrics.success()
                                .channelName(channelName)
                                .hostIndex(Integer.toString(index))
                                .build())
                        .toArray(Meter[]::new);
            } else {
                this.successesPerHost = null;
            }
        }

        private void reshuffled(ImmutableList<HostAndLimitedChannel> newList, long intervalWithJitter) {
            reshuffleMeter.mark();
            if (log.isDebugEnabled()) {
                log.debug(
                        "Reshuffled channels {} {} {} {}",
                        SafeArg.of("nextReshuffle", Duration.ofNanos(intervalWithJitter)),
                        UnsafeArg.of("newList", newList),
                        SafeArg.of("channelName", channelName),
                        SafeArg.of("numChannels", numChannels));
            }
        }

        private void receivedErrorStatus(int pin, HostAndLimitedChannel channel, Response response, OptionalInt next) {
            if (next.isPresent()) {
                nextNodeBecauseResponseCode.mark();
                if (log.isInfoEnabled()) {
                    log.info(
                            "Received error status code, switching to next channel",
                            SafeArg.of("status", response.code()),
                            channel.getHostIdx().safeArg(),
                            SafeArg.of("pin", pin),
                            UnsafeArg.of("channel", channel),
                            SafeArg.of("nextIndex", next.getAsInt()),
                            SafeArg.of("channelName", channelName),
                            SafeArg.of("numChannels", numChannels));
                }
            } else if (log.isDebugEnabled()) {
                log.debug(
                        "Received error status code, but we've already switched",
                        SafeArg.of("status", response.code()),
                        channel.getHostIdx().safeArg(),
                        SafeArg.of("pin", pin),
                        UnsafeArg.of("channel", channel),
                        SafeArg.of("channelName", channelName),
                        SafeArg.of("numChannels", numChannels));
            }
        }

        private void receivedThrowable(int pin, HostAndLimitedChannel channel, Throwable throwable, OptionalInt next) {
            if (next.isPresent()) {
                nextNodeBecauseThrowable.mark();
                if (log.isInfoEnabled()) {
                    // throwable is not shown unless debug logging is
                    // enabled to avoid duplicate stack traces.
                    Throwable throwableToLog = log.isDebugEnabled() ? throwable : null;
                    log.info(
                            "Received throwable, switching to next channel",
                            channel.getHostIdx().safeArg(),
                            SafeArg.of("pin", pin),
                            UnsafeArg.of("channel", channel),
                            SafeArg.of("nextIndex", next.getAsInt()),
                            SafeArg.of("channelName", channelName),
                            SafeArg.of("numChannels", numChannels),
                            throwableToLog);
                }
            } else if (log.isDebugEnabled()) {
                log.debug(
                        "Received throwable, but already switched",
                        SafeArg.of("pin", pin),
                        channel.getHostIdx().safeArg(),
                        UnsafeArg.of("channel", channel),
                        SafeArg.of("channelName", channelName),
                        SafeArg.of("numChannels", numChannels),
                        throwable);
            }
        }

        private void successfulResponse(HostIdx currentHostIdx) {
            if (successesPerHost != null) {
                successesPerHost[currentHostIdx.index()].mark();
            }
        }
    }
}
