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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.ListenableFuture;
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
            List<LimitedChannel> limitedChannels =
                    channels.stream().map(concurrencyLimiter(sim)).collect(Collectors.toList());
            LimitedChannel limited1 = new RoundRobinChannel(limitedChannels);
            return queuedChannelAndRetrying(sim, limited1);
        });
    }

    private static Channel concurrencyLimiterBlacklistRoundRobin(
            Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(concurrencyLimiter(sim))
                    .map(c -> new BlacklistingChannel(c, Duration.ofSeconds(1)))
                    .collect(Collectors.toList());
            LimitedChannel limited1 = new RoundRobinChannel(limitedChannels);
            return queuedChannelAndRetrying(sim, limited1);
        });
    }

    private static Channel pinUntilError(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        Random psuedoRandom = new Random(3218974678L);
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels =
                    channels.stream().map(concurrencyLimiter(sim)).collect(Collectors.toList());
            LimitedChannel limited = new PinUntilErrorChannel(
                    new PinUntilErrorChannel.ReshufflingNodeList(limitedChannels, psuedoRandom, sim.clock()));
            return queuedChannelAndRetrying(sim, limited);
        });
    }

    private static Channel roundRobin(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels =
                    channels.stream().map(Strategy::noOpLimitedChannel).collect(Collectors.toList());
            LimitedChannel limited = new RoundRobinChannel(limitedChannels);
            return queuedChannelAndRetrying(sim, limited);
        });
    }

    private static Function<Channel, LimitedChannel> concurrencyLimiter(Simulation sim) {
        return channel ->
                new ConcurrencyLimitedChannel(channel, () -> ConcurrencyLimitedChannel.createLimiter(sim.clock()));
    }

    private static Channel queuedChannelAndRetrying(Simulation sim, LimitedChannel limited) {
        LimitedChannel limited1 = instrumentClient(limited, sim.taggedMetrics());
        Channel channel = new QueuedChannel(limited1, DispatcherMetrics.of(sim.taggedMetrics()));
        return new RetryingChannel(channel, 4 /* ClientConfigurations.DEFAULT_MAX_NUM_RETRIES */);
    }

    private static LimitedChannel instrumentClient(LimitedChannel delegate, TaggedMetrics metrics) {
        Meter starts = metrics.meter("test_client.starts");
        Counter metric = metrics.counter("test_client.refusals");
        return new LimitedChannel() {

            @Override
            public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
                log.debug(
                        "starting request={}",
                        request.headerParams().get(Benchmark.REQUEST_ID_HEADER).get(0));
                starts.mark();
                Optional<ListenableFuture<Response>> response = delegate.maybeExecute(endpoint, request);
                if (!response.isPresent()) {
                    metric.inc();
                }
                return response;
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
