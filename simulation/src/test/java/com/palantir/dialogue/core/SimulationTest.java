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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.codahale.metrics.Snapshot;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulationTest {
    private static final Logger log = LoggerFactory.getLogger(SimulationTest.class);

    Endpoint endpoint = mock(Endpoint.class);
    Request request = mock(Request.class);

    private Instant realStart = Instant.now();

    @Test
    public void big_simulation() {
        try (Simulation simulation = new Simulation()) {

            SimulationServer server1 = SimulationServer.builder()
                    .metricName("server1")
                    .simulation(simulation)
                    .response(response(200))
                    .responseTime(Duration.ofMillis(200))
                    .build();

            SimulationServer server2 = SimulationServer.builder()
                    .metricName("server2")
                    .simulation(simulation)
                    .response(response(200))
                    .responseTime(Duration.ofMillis(400))
                    .build();

            LimitedChannel idea = new PreferLowestUtilization(ImmutableList.of(server1, server2), simulation.clock());
            Channel channel = dontTolerateLimits(idea);

            ListenableFuture<Void> done = simulation.runParallelRequests(
                    () -> channel.execute(endpoint, request), 100, 4, Duration.ofMillis(100));

            done.addListener(
                    () -> {
                        simulation.metrics().dumpPng(Paths.get("big_simulation.png"));
                    },
                    MoreExecutors.directExecutor());
        }
    }

    @Test
    public void concurrency_limiters() {
        try (Simulation simulation = new Simulation()) {

            SimulationServer server1 = SimulationServer.builder()
                    .metricName("fast_server")
                    .simulation(simulation)
                    .response(response(200))
                    .responseTime(Duration.ofMillis(200))
                    .build();

            SimulationServer server2 = SimulationServer.builder()
                    .metricName("bad_server")
                    .simulation(simulation)
                    .response(response(200))
                    .responseTime(Duration.ofMillis(5000))
                    .untilTime(Duration.ofSeconds(50))
                    .response(response(429))
                    .build();

            ImmutableList<Channel> servers = ImmutableList.of(server1, server2);

            Histogram histogram =
                    new Histogram(new SlidingTimeWindowArrayReservoir(1, TimeUnit.DAYS, simulation.codahaleClock()));

            List<LimitedChannel> limitedChannels = servers.stream()
                    .map(c -> new ConcurrencyLimitedChannel(
                            c, () -> ConcurrencyLimitedChannel.createLimiter(simulation.clock()::read)))
                    // this is a no-op concurrency limiter:
                    // .map(c -> new LimitedChannel() {
                    //     @Override
                    //     public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request)
                    // {
                    //         return Optional.of(c.execute(endpoint, request));
                    //     }
                    // })
                    .collect(Collectors.toList());
            LimitedChannel limited = new RoundRobinChannel(limitedChannels);
            Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(new DefaultTaggedMetricRegistry()));
            channel = new RetryingChannel(channel);
            Channel channel1 = testInstrumentation(histogram, simulation.clock(), channel);

            ListenableFuture<Void> done = simulation.runParallelRequests(
                    () -> channel1.execute(endpoint, request), 1000, 4, Duration.ofMillis(100));

            done.addListener(
                    () -> {
                        log.info(
                                "Simulation finished. Real time={}, simulation time={}",
                                Duration.between(realStart, Instant.now()),
                                Duration.ofNanos(simulation.clock().read()));

                        Snapshot snapshot = histogram.getSnapshot();
                        log.info(
                                "Client-side metrics min={} mean={} p95={} max={}",
                                Duration.ofNanos(snapshot.getMin()),
                                Duration.ofNanos((long) snapshot.getMean()),
                                Duration.ofNanos((long) snapshot.get95thPercentile()),
                                Duration.ofNanos(snapshot.getMax()));

                        // simulation.metrics().dumpCsv(Paths.get("./csv"));
                        simulation.metrics().dumpPng(Paths.get("./par.png"));
                    },
                    MoreExecutors.directExecutor());
        }
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

    private static Channel testInstrumentation(Histogram histogram, Ticker clock, Channel channel) {
        return new Channel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                long start = clock.read();
                ListenableFuture<Response> future = channel.execute(endpoint, request);
                future.addListener(
                        () -> {
                            histogram.update(clock.read() - start);
                        },
                        MoreExecutors.directExecutor());
                return future;
            }
        };
    }

    private static Response response(int status) {
        return SimulationUtils.response(status, "1.0.0");
    }
}
