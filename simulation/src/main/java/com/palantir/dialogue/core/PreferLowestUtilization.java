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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
    private final Randomness randomness;

    public PreferLowestUtilization(ImmutableList<Channel> channels, Ticker clock, Randomness randomness) {
        this.channels = channels;
        this.clock = clock;
        this.randomness = randomness;

        for (Channel channel : channels) {
            active.get(channel); // prefills the cache
        }
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

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        if (channels.isEmpty()) {
            return Optional.empty();
        }

        // we accumulate everything right now (which is probably quite expensive), but it allows us to move on to the
        // next-best channel if our preferred one refuses
        Map<Integer, ImmutableList<Channel>> channelsByActive = active.asMap().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getValue().get(),
                        entry -> ImmutableList.of(entry.getKey()),
                        PreferLowestUtilization::merge,
                        () -> new TreeMap<>()));

        // this relies on the cache being pre-filled (containing some channel -> 0 mappings).
        for (Integer activeCount : channelsByActive.keySet()) {
            ImmutableList<Channel> candidates = channelsByActive.get(activeCount);
            List<Channel> tiebroken = randomness.shuffle(candidates);
            for (Channel channel : tiebroken) {
                log.debug("time={} best={} active={}", Duration.ofNanos(clock.read()), channel, active.asMap());
                ListenableFuture<Response> timed = wrapChannel(endpoint, request, channel);
                return Optional.of(timed);
            }
        }

        // every single channel refused :(
        log.info("Every single thingy refused {}", channelsByActive);
        return Optional.empty();
    }

    private static ImmutableList<Channel> merge(List<Channel> left, List<Channel> right) {
        return ImmutableList.<Channel>builder().addAll(left).addAll(right).build();
    }

    private ListenableFuture<Response> wrapChannel(Endpoint endpoint, Request request, Channel channel) {
        InFlightStage inFlightStage = recordStart(channel, endpoint, request);
        ListenableFuture<Response> response = channel.execute(endpoint, request);
        Futures.addCallback(
                response,
                new FutureCallback<Response>() {
                    @Override
                    public void onSuccess(Response result) {
                        inFlightStage.recordComplete(result, null);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        inFlightStage.recordComplete(null, throwable);
                    }
                },
                MoreExecutors.directExecutor());
        return response;
    }
}
