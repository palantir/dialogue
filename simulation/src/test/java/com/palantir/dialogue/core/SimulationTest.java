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

import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SimulationTest {

    Simulation simulation = new Simulation();
    Endpoint endpoint = mock(Endpoint.class);
    Request request = mock(Request.class);

    @Parameterized.Parameters(name = "{0}")
    public static Strategy[] data() {
        return Strategy.values();
    }

    @Parameterized.Parameter
    public Strategy strategy;

    @Test
    public void fast_and_slow() {
        Channel[] servers = {
            SimulationServer.builder()
                    .metricName("fast")
                    .response(response(200))
                    .responseTime(Duration.ofMillis(200))
                    .simulation(simulation)
                    .build(),
            SimulationServer.builder()
                    .metricName("slow")
                    .response(response(200))
                    .responseTime(Duration.ofSeconds(20)) // <- mega slow!
                    .simulation(simulation)
                    .build()
        };

        Channel channel = strategy.getChannel.apply(simulation, servers);

        Benchmark.builder()
                .numRequests(1000)
                .requestsPerSecond(20)
                .channel(i -> channel.execute(endpoint, request))
                .simulation(simulation)
                .onCompletion(() -> {
                    simulation.metrics().dumpPng(Paths.get(strategy + ".png"));
                })
                .run();
    }

    public enum Strategy {
        LOWEST_UTILIZATION(SimulationTest::lowestUtilization),
        CONCURRENCY_LIMITER(SimulationTest::concurrencyLimiter),
        ROUND_ROBIN(SimulationTest::roundRobin);

        private final BiFunction<Simulation, Channel[], Channel> getChannel;

        Strategy(BiFunction<Simulation, Channel[], Channel> getChannel) {
            this.getChannel = getChannel;
        }
    }

    private static Response response(int status) {
        return SimulationUtils.response(status, "1.0.0");
    }

    private static Channel lowestUtilization(Simulation sim, Channel... channels) {
        LimitedChannel idea = new PreferLowestUtilization(ImmutableList.copyOf(channels), sim.clock());
        return dontTolerateLimits(idea);
    }

    private static Channel concurrencyLimiter(Simulation sim, Channel... channels) {
        List<LimitedChannel> limitedChannels = Stream.of(channels)
                .map(c -> new ConcurrencyLimitedChannel(
                        c, () -> ConcurrencyLimitedChannel.createLimiter(sim.clock()::read)))
                .collect(Collectors.toList());
        LimitedChannel limited = new RoundRobinChannel(limitedChannels);
        Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(new DefaultTaggedMetricRegistry()));
        return new RetryingChannel(channel);
    }

    private static Channel roundRobin(Simulation sim, Channel... channels) {
        List<LimitedChannel> limitedChannels = Stream.of(channels)
                .map(c -> (LimitedChannel) (endpoint, request) -> Optional.of(c.execute(endpoint, request)))
                .collect(Collectors.toList());
        LimitedChannel limited = new RoundRobinChannel(limitedChannels);
        Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(new DefaultTaggedMetricRegistry()));
        return new RetryingChannel(channel);
    }

    private static Channel dontTolerateLimits(LimitedChannel limitedChannel) {
        return new Channel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                return limitedChannel
                        .maybeExecute(endpoint, request)
                        .orElseThrow(() -> new RuntimeException("Got limited :("));
            }
        };
    }
}
