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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes all requests to one host until an error is received, then we'll move on to the next host.
 * Upside: ensures clients will always hit warm caches
 * Downside: a single high volume client can result in unbalanced utilization of server nodes.
 *
 * Reshuffles all nodes
 */
public final class PinUntilErrorChannel implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PinUntilErrorChannel.class);

    // TODO(dfox): could we make this endpoint-specific?
    private final AtomicInteger currentHost = new AtomicInteger(0); // increases forever (use toIndex)
    private final Optional<Reshuffle> reshuffle;

    private volatile ImmutableList<LimitedChannel> channels;

    @VisibleForTesting
    PinUntilErrorChannel(List<LimitedChannel> channels, Random random, Ticker clock) {
        this.channels = shuffleImmutableList(channels, random);
        this.reshuffle = Optional.of(new Reshuffle(random, clock));
    }

    @VisibleForTesting
    PinUntilErrorChannel(List<LimitedChannel> channels, Random random) {
        this.channels = shuffleImmutableList(channels, random);
        this.reshuffle = Optional.empty();
    }

    public static PinUntilErrorChannel pinUntilError(List<LimitedChannel> channels) {
        return new PinUntilErrorChannel(channels, ThreadLocalRandom.current(), System::nanoTime);
    }

    /** @deprecated prefer {@link #pinUntilError}, as this strategy results in unbalanced server utilization. */
    @Deprecated
    public static PinUntilErrorChannel pinUntilErrorWithoutReshuffle(List<LimitedChannel> channels) {
        return new PinUntilErrorChannel(channels, ThreadLocalRandom.current());
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        if (channels.isEmpty()) {
            log.info("Rejecting request due to no delegates");
            return Optional.empty();
        }

        reshuffle.ifPresent(Reshuffle::reshuffleChannelsIfNecessary);

        int currentIndex = toIndex(currentHost.get());
        LimitedChannel channel = channels.get(currentIndex);

        Optional<ListenableFuture<Response>> maybeFuture = channel.maybeExecute(endpoint, request);
        if (!maybeFuture.isPresent()) {
            OptionalInt next = incrementCurrentHost(currentIndex);
            log.info(
                    "Current channel rejected request, switching to next channel",
                    SafeArg.of("currentIndex", currentIndex),
                    UnsafeArg.of("current", channel),
                    SafeArg.of("nextIndex", next));
            return Optional.empty(); // if the caller retries immediately, we'll get the next host
        }

        ListenableFuture<Response> future = maybeFuture.get();

        DialogueFutures.addDirectCallback(future, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response response) {
                if (response.code() / 100 <= 2) {
                    // we consider all the 1xx and 2xx codes successful, so want to remain pinned to this channel
                    return;
                }

                // TODO(dfox): handle 308 See Other somehow (don't currently know host -> channel mapping)

                OptionalInt next = incrementCurrentHost(currentIndex);
                log.info(
                        "Received error status code, switching to next channel",
                        SafeArg.of("status", response.code()),
                        SafeArg.of("currentIndex", currentIndex),
                        UnsafeArg.of("current", channel),
                        SafeArg.of("nextIndex", next));
            }

            @Override
            public void onFailure(Throwable throwable) {
                OptionalInt next = incrementCurrentHost(currentIndex);
                log.info(
                        "Received throwable, switching to next channel",
                        SafeArg.of("currentIndex", currentIndex),
                        UnsafeArg.of("current", channel),
                        SafeArg.of("nextIndex", next),
                        throwable);
            }
        });

        return Optional.of(future);
    }

    /**
     * If we have some reason to think the currentIndex is bad, we want to move to the next host. This is done with a
     * compareAndSet to ensure that
     */
    private OptionalInt incrementCurrentHost(int currentIndex) {
        int next = toIndex(currentIndex + 1);
        boolean saved = currentHost.compareAndSet(currentIndex, next);
        return saved ? OptionalInt.of(next) : OptionalInt.empty(); // we've moved on already
    }

    private int toIndex(int value) {
        return value % channels.size();
    }

    private final class Reshuffle {
        private final Ticker clock;
        private final Random random;
        private final long intervalWithJitter;
        private final AtomicLong nextReshuffle;

        private Reshuffle(Random random, Ticker clock) {
            this.random = random;
            // we add some jitter to ensure that there isn't a big spike of reshuffling every 10 minutes.
            this.intervalWithJitter = Duration.ofMinutes(10)
                    .plus(Duration.ofSeconds(random.nextInt(60) - 30))
                    .toNanos();
            this.nextReshuffle = new AtomicLong(clock.read() + intervalWithJitter);
            this.clock = clock;
        }

        private void reshuffleChannelsIfNecessary() {
            long reshuffleTime = nextReshuffle.get();
            if (clock.read() < reshuffleTime) {
                return;
            }

            if (nextReshuffle.compareAndSet(reshuffleTime, reshuffleTime + intervalWithJitter)) {
                ImmutableList<LimitedChannel> newList = shuffleImmutableList(channels, random);
                log.debug(
                        "Reshuffling channels {} {}",
                        SafeArg.of("nextReshuffle", Duration.ofNanos(intervalWithJitter)),
                        UnsafeArg.of("newList", newList));
                channels = newList;
            }
        }
    }

    /** Returns a new shuffled list, without mutating the input list (which may be immutable). */
    private static <T> ImmutableList<T> shuffleImmutableList(List<T> sourceList, Random random) {
        ArrayList<T> mutableList = new ArrayList<>(sourceList);
        Collections.shuffle(mutableList, random);
        return ImmutableList.copyOf(mutableList);
    }
}
