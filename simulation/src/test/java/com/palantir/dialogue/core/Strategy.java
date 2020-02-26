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
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("ImmutableEnumChecker")
public enum Strategy {
    CONCURRENCY_LIMITER_ROUND_ROBIN(Strategy::concurrencyLimiter),
    CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN(Strategy::concurrencyLimiterBlacklistRoundRobin),
    CONCURRENCY_LIMITER_PIN_UNTIL_ERROR(Strategy::pinUntilError),
    UNLIMITED_ROUND_ROBIN(Strategy::roundRobin);

    private static final Logger log = LoggerFactory.getLogger(Strategy.class);
    private final BiFunction<Simulation, Supplier<List<SimulationServer>>, Channel> getChannel;

    Strategy(BiFunction<Simulation, Supplier<List<SimulationServer>>, Channel> getChannel) {
        this.getChannel = getChannel;
    }

    public Channel getChannel(Simulation simulation, Supplier<List<SimulationServer>> servers) {
        return getChannel.apply(simulation, servers);
    }

    private static Channel concurrencyLimiter(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(addConcurrencyLimiter(sim))
                    .map(addFixedLimiter(sim))
                    .collect(Collectors.toList());
            Channel channel = new RoundRobinChannel(limitedChannels);
            return retryingChannel(sim, channel);
        });
    }

    private static Channel concurrencyLimiterBlacklistRoundRobin(
            Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(addConcurrencyLimiter(sim))
                    .map(addFixedLimiter(sim))
                    .map(c -> new BlacklistingChannel(c, Duration.ofSeconds(1), sim.clock()))
                    .collect(Collectors.toList());
            Channel channel = new RoundRobinChannel(limitedChannels);
            return retryingChannel(sim, channel);
        });
    }

    private static Channel pinUntilError(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        Random psuedoRandom = new Random(3218974678L);
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(addConcurrencyLimiter(sim))
                    .map(addFixedLimiter(sim))
                    .collect(Collectors.toList());
            DialoguePinuntilerrorMetrics metrics = DialoguePinuntilerrorMetrics.of(sim.taggedMetrics());
            Channel channel = new PinUntilErrorChannel(
                    new PinUntilErrorChannel.ReshufflingNodeList(limitedChannels, psuedoRandom, sim.clock(), metrics),
                    metrics);
            return retryingChannel(sim, channel);
        });
    }

    private static Channel roundRobin(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(Strategy::noOpLimitedChannel)
                    .map(addFixedLimiter(sim))
                    .collect(Collectors.toList());
            Channel channel = new RoundRobinChannel(limitedChannels);
            return retryingChannel(sim, channel);
        });
    }

    private static Function<Channel, LimitedChannel> addConcurrencyLimiter(Simulation sim) {
        return channel -> new ConcurrencyLimitedChannel(
                new ChannelToLimitedChannelAdapter(channel),
                ConcurrencyLimitedChannel.createLimiter(sim.clock()),
                DialogueClientMetrics.of(sim.taggedMetrics()));
    }

    private static Function<LimitedChannel, LimitedChannel> addFixedLimiter(Simulation sim) {
        return channel -> new FixedLimitedChannel(channel, 256, DialogueClientMetrics.of(sim.taggedMetrics()));
    }

    private static Channel retryingChannel(Simulation sim, Channel channel) {
        Channel instrumented = instrumentClient(channel, sim.taggedMetrics());
        return new RetryingChannel(
                instrumented,
                4 /* ClientConfigurations.DEFAULT_MAX_NUM_RETRIES */,
                Duration.ofMillis(250) /* ClientConfigurations.DEFAULT_BACKOFF_SLOT_SIZE */,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED,
                sim.scheduler(),
                new Random(8 /* Guaranteed lucky */)::nextDouble);
    }

    private static Channel instrumentClient(Channel delegate, TaggedMetrics metrics) {
        Meter starts = metrics.meter("test_client.starts");
        return new Channel() {

            @Override
            public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                log.debug(
                        "starting request={}",
                        request.headerParams().get(Benchmark.REQUEST_ID_HEADER).get(0));
                starts.mark();
                return delegate.execute(endpoint, request);
            }

            @Override
            public String toString() {
                return delegate.toString();
            }
        };
    }

    private static LimitedChannel noOpLimitedChannel(Channel delegate) {
        return new LimitedChannel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                return delegate.execute(endpoint, request);
            }

            @Override
            public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
                return Optional.of(delegate.execute(endpoint, request));
            }

            @Override
            public String toString() {
                return delegate.toString();
            }
        };
    }
}
