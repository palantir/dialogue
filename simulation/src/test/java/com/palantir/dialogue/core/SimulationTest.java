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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
                    .response(response(200, "1.56.0"))
                    .responseTime(Duration.ofMillis(200))
                    .untilTime(Duration.ofSeconds(5))
                    .response(response(500, "1.56.1")) // simulating a bad blue/green here
                    .responseTime(Duration.ofSeconds(20))
                    // .untilNthRequest(20)
                    // .response(response(200, "1.56.0"))
                    // .responseTime(Duration.ofSeconds(2))
                    .build();

            SimulationServer server2 = SimulationServer.builder()
                    .metricName("server2")
                    .simulation(simulation)
                    .response(response(200, "1.56.0"))
                    .responseTime(Duration.ofMillis(400))
                    .build();

            LimitedChannel idea =
                    new PreferLowestUpstreamUtilization(ImmutableList.of(server1, server2), simulation.clock());
            Channel channel = dontTolerateLimits(idea);

            fireOffBatches(simulation, channel, 100, 4, Duration.ofMillis(100));
        }
    }

    @Test
    public void concurrency_limiters() {
        try (Simulation simulation = new Simulation()) {

            SimulationServer server1 = SimulationServer.builder()
                    .metricName("fast_server")
                    .simulation(simulation)
                    .response(response(200, "1.56.0"))
                    .responseTime(Duration.ofMillis(200))
                    .build();

            SimulationServer server2 = SimulationServer.builder()
                    .metricName("bad_server")
                    .simulation(simulation)
                    .response(response(200, "1.56.0"))
                    .responseTime(Duration.ofMillis(5000))
                    .untilTime(Duration.ofSeconds(50))
                    .response(response(429, "1.56.0"))
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
            channel = testInstrumentation(histogram, simulation.clock(), channel);

            SettableFuture<Void> done = fireOffBatches(simulation, channel, 1000, 4, Duration.ofMillis(100));

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
                        // simulation.metrics().dumpPng(Paths.get("./par.png"));
                    },
                    MoreExecutors.directExecutor());

            // fireOffSerialRequests(simulation, thingWeAreTesting, 30);
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

    private SettableFuture<Void> fireOffBatches(
            Simulation simulation, Channel channel, int numBatches, int batchSize, Duration batchDelay) {

        Runnable stopReporting = simulation.metrics().startReporting(Duration.ofSeconds(1));

        SettableFuture<Void> done = SettableFuture.create();
        done.addListener(stopReporting, MoreExecutors.directExecutor());

        int total = numBatches * batchSize;
        AtomicInteger outstanding = new AtomicInteger(total);
        for (int batchNum = 0; batchNum < numBatches; batchNum++) {
            simulation.schedule(
                    () -> {
                        for (int i = 0; i < batchSize; i++) {

                            ListenableFuture<Response> serverFuture = channel.execute(endpoint, request);

                            Futures.transformAsync(
                                    serverFuture,
                                    resp -> {
                                        if (outstanding.decrementAndGet() == 0) {
                                            done.set(null);
                                        }

                                        return Futures.immediateFuture(null);
                                    },
                                    MoreExecutors.directExecutor());
                        }
                    },
                    batchNum * batchDelay.toNanos(),
                    TimeUnit.NANOSECONDS);
        }

        return done;
    }

    // private void fireOffSerialRequests(
    //         Simulation simulation, PreferLowestUpstreamUtilization thingWeAreTesting, int numRequests) {
    //
    //     Runnable stopReporting = simulation.metrics().startReporting(Duration.ofSeconds(1));
    //
    //     // fire off numRequests in a hot loop
    //     ListenableFuture<Integer> roundTrip = Futures.immediateFuture(0);
    //     for (int i = 0; i < numRequests; i++) {
    //         roundTrip = Futures.transformAsync(
    //                 roundTrip,
    //                 number -> {
    //                     Optional<Statistics.Upstream> best = thingWeAreTesting.getBest(endpoint);
    //                     assertThat(best).isPresent();
    //
    //                     Statistics.Upstream upstream = best.get();
    //                     SimulationServer server = nodeToServer.get(upstream);
    //                     log.info(
    //                             "time={} request={} upstream={}",
    //                             simulation.clock().read(),
    //                             number,
    //                             server);
    //
    //                     Statistics.InFlightStage inFlight = thingWeAreTesting.recordStart(upstream, endpoint, request);
    //                     ListenableFuture<Response> serverFuture = server.execute(endpoint, request);
    //                     return Futures.transformAsync(
    //                             serverFuture,
    //                             resp -> {
    //                                 inFlight.recordComplete(resp, null);
    //                                 return Futures.immediateFuture(number + 1);
    //                             },
    //                             MoreExecutors.directExecutor());
    //                 },
    //                 MoreExecutors.directExecutor());
    //     }
    //     roundTrip.addListener(
    //             () -> {
    //                 stopReporting.run();
    //                 log.info(
    //                         "Simulation finished. Real time={}, simulation time={}",
    //                         Duration.between(realStart, Instant.now()),
    //                         Duration.ofNanos(simulation.clock().read()));
    //
    //                 // simulation.metrics().dumpCsv(Paths.get("./csv"));
    //                 simulation.metrics().dumpPng(Paths.get("./metrics.png"));
    //             },
    //             MoreExecutors.directExecutor());
    // }

    private static Response response(int status, String version) {
        return SimulationUtils.response(status, version);
    }
}
