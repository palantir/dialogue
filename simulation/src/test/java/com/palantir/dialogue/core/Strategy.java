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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("ImmutableEnumChecker")
public enum Strategy {
    CONCURRENCY_LIMITER(Strategy::concurrencyLimiter),
    ROUND_ROBIN(Strategy::roundRobin);

    private static final Logger log = LoggerFactory.getLogger(Strategy.class);
    private final BiFunction<Simulation, Supplier<List<SimulationServer>>, Channel> getChannel;

    Strategy(BiFunction<Simulation, Supplier<List<SimulationServer>>, Channel> getChannel) {
        this.getChannel = getChannel;
    }

    public Channel getChannel(Simulation simulation, Supplier<List<SimulationServer>> servers) {
        return getChannel.apply(simulation, servers);
    }

    private static Channel concurrencyLimiter(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        return GenericRefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels1 = channels.stream()
                    .map(c1 -> new ConcurrencyLimitedChannel(
                            c1, () -> ConcurrencyLimitedChannel.createLimiter(sim.clock()::read)))
                    .collect(Collectors.toList());
            LimitedChannel limited1 = new RoundRobinChannel(limitedChannels1);
            limited1 = instrumentClient(limited1, sim.metrics()); // just for debugging
            Channel channel = new QueuedChannel(limited1, DispatcherMetrics.of(new DefaultTaggedMetricRegistry()));
            return Optional.of(new RetryingChannel(channel));
        });
    }

    private static Channel roundRobin(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        return GenericRefreshingChannel.create(channelSupplier, channels -> {
            List<LimitedChannel> limitedChannels =
                    channels.stream().map(Strategy::noOpLimitedChannel).collect(Collectors.toList());
            LimitedChannel limited = new RoundRobinChannel(limitedChannels);
            limited = instrumentClient(limited, sim.metrics()); // will always be zero due to the noOpLimitedChannel
            Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(new DefaultTaggedMetricRegistry()));
            return Optional.of(new RetryingChannel(channel));
        });
    }

    private static LimitedChannel instrumentClient(LimitedChannel delegate, SimulationMetrics metrics) {
        Meter starts = metrics.meter("test_client.starts");
        Counter metric = metrics.counter("test_client.refusals");
        return new LimitedChannel() {

            @Override
            public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
                log.info("starting traceid={}", request.headerParams().get("X-B3-TraceId"));
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

    /** This is an alternative to the {@link com.palantir.dialogue.core.QueuedChannel}. */
    static Channel dontTolerateLimits(LimitedChannel limitedChannel) {
        return new Channel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                Optional<ListenableFuture<Response>> future = limitedChannel.maybeExecute(endpoint, request);
                if (future.isPresent()) {
                    return future.get();
                }

                return Futures.immediateFailedFuture(new SafeRuntimeException("limited channel says no :("));
            }

            @Override
            public String toString() {
                return limitedChannel.toString();
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
