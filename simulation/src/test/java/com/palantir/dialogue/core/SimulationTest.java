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
import static org.mockito.Mockito.when;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.knowm.xchart.XYChart;

/**
 * The following sccenarios are important for good clients to handle.
 * <ol>
 *     <li>Normal operation: some node node is maybe 10-20% slower (e.g. maybe it's further away)
 *     <li>Fast failures (500/503/429) with revert: upgrading one node means everything gets insta 500'd (also 503 /
 *     429)
 *     <li>Slow failures (500/503/429) with revert: upgrading one node means all requests get slow and also return
 *     bad errors
 *     <li>Drastic slowdown with revert: One node suddenly starts taking 10 seconds to return, or possibly starts black
 *     holing traffic (e.g. some horrible spike in traffic / STW GC)
 *     <li>All nodes return 500s briefly (ideally clients could queue up if they're willing to wait)
 * </ol>
 *
 * Heuristics should work sensibly for a variety of server response times (incl 1ms, 10ms, 100ms and 1s).
 * We usually have O(10) upstream nodes. Live-reloading node list shouldn't go crazy.
 *
 * Goals:
 * <ol>
 *     <li>Minimize user-perceived failures
 *     <li>Minimize user-perceived mean response
 *     <li>Minimize server CPU time spent
 * </ol>
 */
@RunWith(Parameterized.class)
public class SimulationTest {
    private static final Endpoint ENDPOINT = mock(Endpoint.class);

    @Parameterized.Parameters(name = "{0}")
    public static Strategy[] data() {
        return Strategy.values();
    }

    @Rule
    public final TestName testName = new TestName();

    @Parameterized.Parameter
    public Strategy strategy;

    private final Simulation simulation = new Simulation();
    private Benchmark.BenchmarkResult result;

    @SuppressWarnings("ImmutableEnumChecker")
    public enum Strategy {
        LOWEST_UTILIZATION(SimulationTest::lowestUtilization),
        CONCURRENCY_LIMITER(SimulationTest::concurrencyLimiter),
        ROUND_ROBIN(SimulationTest::roundRobin);

        private final BiFunction<Simulation, Channel[], Channel> getChannel;

        Strategy(BiFunction<Simulation, Channel[], Channel> getChannel) {
            this.getChannel = getChannel;
        }
    }

    @Test
    public void simplest_possible_case() {
        Channel[] servers = {
            SimulationServer.builder()
                    .metricName("fast")
                    .response(response(200))
                    .responseTimeConstant(Duration.ofMillis(600)) // this isn't very realistic
                    .simulation(simulation)
                    .build(),
            SimulationServer.builder()
                    .metricName("medium")
                    .response(response(200))
                    .responseTimeConstant(Duration.ofMillis(800))
                    .simulation(simulation)
                    .build(),
            SimulationServer.builder()
                    .metricName("slightly_slow")
                    .response(response(200))
                    .responseTimeConstant(Duration.ofMillis(1000))
                    .simulation(simulation)
                    .build()
        };

        Channel channel = strategy.getChannel.apply(simulation, servers);

        result = Benchmark.builder()
                .requestsPerSecond(50)
                .numRequests(2000)
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void slow_503s_then_revert() {
        int capacity = 60;
        Channel[] servers = {
            SimulationServer.builder()
                    .metricName("fast")
                    .response(response(200))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .simulation(simulation)
                    .build(),
            SimulationServer.builder()
                    .metricName("slow_failures_then_revert")
                    .response(response(200))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .simulation(simulation)
                    // at this point, the server starts returning failures very slowly
                    .untilTime(Duration.ofSeconds(3))
                    .responseTimeUpToCapacity(Duration.ofSeconds(1), capacity)
                    .response(response(503))
                    // then we revert
                    .untilTime(Duration.ofSeconds(10))
                    .response(response(200))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .build()
        };

        Channel channel = strategy.getChannel.apply(simulation, servers);

        result = Benchmark.builder()
                .requestsPerSecond(200)
                .numRequests(3000) // something weird happens at 1811... bug in DeterministicScheduler?
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void fast_500s_then_revert() {
        int capacity = 60;
        Channel[] servers = {
            SimulationServer.builder()
                    .metricName("fast")
                    .response(response(200))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .simulation(simulation)
                    .build(),
            SimulationServer.builder()
                    .metricName("fast_500s_the_revert")
                    .response(response(200))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .simulation(simulation)
                    // at this point, the server starts returning failures instantly
                    .untilTime(Duration.ofSeconds(3))
                    .responseTimeUpToCapacity(Duration.ofMillis(10), capacity)
                    .response(response(500))
                    // then we revert
                    .untilTime(Duration.ofSeconds(10))
                    .response(response(200))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .build()
        };

        Channel channel = strategy.getChannel.apply(simulation, servers);

        result = Benchmark.builder()
                .requestsPerSecond(250)
                .sendUntil(Duration.ofSeconds(15))
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void drastic_slowdown() {
        int capacity = 60;
        Channel[] servers = {
            SimulationServer.builder()
                    .metricName("fast")
                    .response(response(200))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .simulation(simulation)
                    .build(),
            SimulationServer.builder()
                    .metricName("fast_then_slow_then_fast")
                    .response(response(200))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .simulation(simulation)
                    // at this point, the server starts returning failures instantly
                    .untilTime(Duration.ofSeconds(3))
                    .responseTimeUpToCapacity(Duration.ofSeconds(10), capacity)
                    // then we revert
                    .untilTime(Duration.ofSeconds(10))
                    .responseTimeUpToCapacity(Duration.ofMillis(60), capacity)
                    .build()
        };

        Channel channel = strategy.getChannel.apply(simulation, servers);

        result = Benchmark.builder()
                .requestsPerSecond(200)
                .numRequests(4000)
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void all_nodes_500() {
        int capacity = 60;
        Channel[] servers = {
            SimulationServer.builder()
                    .metricName("node1")
                    .response(response(200))
                    .responseTimeConstant(Duration.ofMillis(600))
                    .simulation(simulation)
                    // at this point, the server starts returning failures at the same speed
                    .untilTime(Duration.ofSeconds(3))
                    .response(response(500))
                    // then we revert
                    .untilTime(Duration.ofSeconds(10))
                    .response(response(200))
                    .build(),
            SimulationServer.builder()
                    .metricName("node2")
                    .response(response(200))
                    .responseTimeConstant(Duration.ofMillis(600))
                    .simulation(simulation)
                    // at this point, the server starts returning failures at the same speed
                    .untilTime(Duration.ofSeconds(3))
                    .response(response(500))
                    // then we revert
                    .untilTime(Duration.ofSeconds(10))
                    .response(response(200))
                    .build()
        };

        Channel channel = strategy.getChannel.apply(simulation, servers);

        result = Benchmark.builder()
                .requestsPerSecond(20)
                .numRequests(400)
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @After
    public void after() {
        String title = String.format(
                "%s client_mean=%s success=%s%%",
                strategy, Duration.ofNanos((long) result.clientHistogram().getMean()), result.successPercentage());

        XYChart activeRequests = simulation.metrics().chart(Pattern.compile("active"));
        activeRequests.setTitle(title);

        XYChart clientStuff = simulation.metrics().chart(Pattern.compile("(refusals|starts).count"));
        XYChart serverRequestCount = simulation.metrics().chart(Pattern.compile("request.*count"));

        SimulationMetrics.png(
                testName.getMethodName() + ".png",
                activeRequests,
                serverRequestCount,
                clientStuff);
    }

    private static Response response(int status) {
        return SimulationUtils.response(status, "1.0.0");
    }

    private static Request request(String traceId) {
        Request req = mock(Request.class);
        when(req.headerParams()).thenReturn(ImmutableMap.of("X-B3-TraceId", traceId));
        return req;
    }

    private static Channel lowestUtilization(Simulation sim, Channel... channels) {
        ImmutableList<LimitedChannel> limitedChannels = Arrays.stream(channels)
                .map(SimulationTest::noOpLimitedChannel)
                .map(c -> new BlacklistingChannel(c, Duration.ofSeconds(1), sim.clock()))
                .collect(ImmutableList.toImmutableList());
        LimitedChannel limited =
                new PreferLowestUtilization(limitedChannels, sim.clock(), SimulationUtils.newPseudoRandom());
        limited = instrumentClient(limited, sim.metrics()); // just for debugging
        Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(new DefaultTaggedMetricRegistry()));
        // Channel channel = dontTolerateLimits(limited);
        return new RetryingChannel(channel);
    }

    private static Channel concurrencyLimiter(Simulation sim, Channel... channels) {
        List<LimitedChannel> limitedChannels = Stream.of(channels)
                .map(c -> new ConcurrencyLimitedChannel(
                        c, () -> ConcurrencyLimitedChannel.createLimiter(sim.clock()::read)))
                .collect(Collectors.toList());
        LimitedChannel limited = new RoundRobinChannel(limitedChannels);
        limited = instrumentClient(limited, sim.metrics()); // just for debugging
        Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(new DefaultTaggedMetricRegistry()));
        return new RetryingChannel(channel);
    }

    private static Channel roundRobin(Simulation sim, Channel... channels) {
        List<LimitedChannel> limitedChannels = Stream.of(channels)
                .map(SimulationTest::noOpLimitedChannel)
                .collect(Collectors.toList());
        LimitedChannel limited = new RoundRobinChannel(limitedChannels);
        limited = instrumentClient(limited, sim.metrics()); // will always be zero due to the noOpLimitedChannel
        Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(new DefaultTaggedMetricRegistry()));
        return new RetryingChannel(channel);
    }

    private static LimitedChannel instrumentClient(LimitedChannel delegate, SimulationMetrics metrics) {
        Meter starts = metrics.meter("test_client.starts");
        Counter metric = metrics.counter("test_client.refusals");
        return new LimitedChannel() {

            @Override
            public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
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

    private static Channel dontTolerateLimits(LimitedChannel limitedChannel) {
        return new Channel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                Optional<ListenableFuture<Response>> future = limitedChannel.maybeExecute(endpoint, request);
                if (future.isPresent()) {
                    return future.get();
                }

                return Futures.immediateFailedFuture(new RuntimeException("limited channel says no :("));
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
