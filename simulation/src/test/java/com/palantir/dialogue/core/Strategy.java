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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.logsafe.Preconditions;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
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
                    .map(StatusCodeConvertingChannel::new)
                    .map(addConcurrencyLimiter(sim))
                    .map(addFixedLimiter(sim))
                    .collect(Collectors.toList());
            LimitedChannel limited1 = new RoundRobinChannel(limitedChannels);
            return queuedChannelAndRetrying(sim, limited1);
        });
    }

    private static Channel concurrencyLimiterBlacklistRoundRobin(
            Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        DeferredLimitedChannelListener listener = new DeferredLimitedChannelListener();
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(StatusCodeConvertingChannel::new)
                    .map(addConcurrencyLimiter(sim))
                    .map(addFixedLimiter(sim))
                    .map(c -> new BlacklistingChannel(c, Duration.ofSeconds(1), listener, sim.clock(), sim.scheduler()))
                    .collect(Collectors.toList());
            LimitedChannel limited1 = new RoundRobinChannel(limitedChannels);
            return queuedChannelAndRetrying(sim, limited1, listener);
        });
    }

    private static Channel pinUntilError(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        Random psuedoRandom = new Random(3218974678L);
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(StatusCodeConvertingChannel::new)
                    .map(addConcurrencyLimiter(sim))
                    .map(addFixedLimiter(sim))
                    .collect(Collectors.toList());
            LimitedChannel limited = new PinUntilErrorChannel(
                    new PinUntilErrorChannel.ReshufflingNodeList(limitedChannels, psuedoRandom, sim.clock()));
            return queuedChannelAndRetrying(sim, limited);
        });
    }

    private static Channel roundRobin(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(StatusCodeConvertingChannel::new)
                    .map(addFixedLimiter(sim))
                    .collect(Collectors.toList());
            LimitedChannel limited = new RoundRobinChannel(limitedChannels);
            return queuedChannelAndRetrying(sim, limited);
        });
    }

    private static Function<LimitedChannel, LimitedChannel> addConcurrencyLimiter(Simulation sim) {
        return channel -> new ConcurrencyLimitedChannel(
                channel,
                ConcurrencyLimitedChannel.createLimiter(sim.clock()),
                DialogueClientMetrics.of(sim.taggedMetrics()));
    }

    private static Function<LimitedChannel, LimitedChannel> addFixedLimiter(Simulation sim) {
        return channel -> new FixedLimitedChannel(channel, 256, DialogueClientMetrics.of(sim.taggedMetrics()));
    }

    private static Channel queuedChannelAndRetrying(Simulation sim, LimitedChannel limited) {
        return queuedChannelAndRetrying(sim, limited, new DeferredLimitedChannelListener());
    }

    private static Channel queuedChannelAndRetrying(
            Simulation sim, LimitedChannel limited, DeferredLimitedChannelListener listener) {
        LimitedChannel limited1 = instrumentClient(limited, sim.taggedMetrics());
        Channel channel = new RetryingChannel(
                limited1,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                new RetryingChannel.ExponentialBackoff(
                        4 /* ClientConfigurations.DEFAULT_MAX_NUM_RETRIES */, Duration.ofMillis(250)),
                sim.scheduler());
        // listener.delegate = channel::schedule;

        return channel;
    }

    private static LimitedChannel instrumentClient(LimitedChannel delegate, TaggedMetrics metrics) {
        Meter starts = metrics.meter("test_client.starts");
        Counter metric = metrics.counter("test_client.refusals");
        return new LimitedChannel() {

            @Override
            public ListenableFuture<LimitedResponse> maybeExecute(Endpoint endpoint, Request request) {
                log.debug(
                        "starting request={}",
                        request.headerParams().get(Benchmark.REQUEST_ID_HEADER).get(0));
                starts.mark();
                return DialogueFutures.addDirectCallback(
                        delegate.maybeExecute(endpoint, request), new FutureCallback<LimitedResponse>() {
                            @Override
                            public void onSuccess(@Nullable LimitedResponse result) {
                                if (result.matches(LimitedResponse.isClientLimited)) {
                                    metric.inc();
                                }
                            }

                            @Override
                            public void onFailure(Throwable _throwable) {}
                        });
            }

            @Override
            public String toString() {
                return delegate.toString();
            }
        };
    }

    private static final class DeferredLimitedChannelListener implements LimitedChannelListener {
        private LimitedChannelListener delegate;

        @Override
        public void onChannelReady() {
            Preconditions.checkNotNull(delegate, "Delegate listener has not been initialized")
                    .onChannelReady();
        }
    }
}
