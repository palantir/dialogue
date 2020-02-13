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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codahale.metrics.Counter;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulationTest {
    private static final Logger log = LoggerFactory.getLogger(SimulationTest.class);

    Endpoint endpoint = mock(Endpoint.class);
    Request request = mock(Request.class);

    Statistics.Upstream node1 = ImmutableUpstream.of("node1");
    Statistics.Upstream node2 = ImmutableUpstream.of("node2");
    private Instant realStart = Instant.now();
    private ImmutableMap<Statistics.Upstream, SimulationServer> nodeToServer;

    @Test
    public void big_simulation() {
        try (Simulation simulation = new Simulation()) {

            SimulationServer server1 = SimulationServer.builder()
                    .metricName("server1")
                    .simulation(simulation)
                    .response(response(200, "1.56.0"))
                    .responseTime(Duration.ofSeconds(2))
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
                    .responseTime(Duration.ofSeconds(1))
                    .build();

            nodeToServer = ImmutableMap.of(node1, server1, node2, server2);

            PreferLowestUpstreamUtilization thingWeAreTesting =
                    new PreferLowestUpstreamUtilization(() -> ImmutableList.of(node1, node2));
            // StatisticsImpl thingWeAreTesting = stats(simulation.clock(), this.node1, this.node2);

            fireOffBatches(simulation, thingWeAreTesting, 100, 4, Duration.ofMillis(100));

            // fireOffSerialRequests(simulation, thingWeAreTesting, 30);
        }
    }

    private void fireOffBatches(
            Simulation simulation,
            PreferLowestUpstreamUtilization thingWeAreTesting,
            int numBatches,
            int batchSize,
            Duration batchDelay) {

        Counter requestStarted = simulation.metrics().counter("test.request.started");

        Runnable stopReporting = simulation.metrics().startReporting(Duration.ofSeconds(1));

        int total = numBatches * batchSize;
        AtomicInteger outstanding = new AtomicInteger(total);
        for (int batchNum = 0; batchNum < numBatches; batchNum++) {
            simulation.schedule(
                    () -> {
                        for (int i = 0; i < batchSize; i++) {
                            Statistics.Upstream upstream =
                                    thingWeAreTesting.getBest(endpoint).get();

                            log.debug("time={} best={}", Duration.ofNanos(simulation.clock().read()), upstream);
                            SimulationServer server = nodeToServer.get(upstream);

                            requestStarted.inc();
                            Statistics.InFlightStage inFlight =
                                    thingWeAreTesting.recordStart(upstream, endpoint, request);
                            ListenableFuture<Response> serverFuture = server.handleRequest(endpoint, request);
                            serverFuture.addListener(() -> {}, MoreExecutors.directExecutor());

                            Futures.transformAsync(
                                    serverFuture,
                                    resp -> {
                                        inFlight.recordComplete(resp, null);

                                        if (outstanding.decrementAndGet() == 0) {
                                            stopReporting.run();
                                            log.info(
                                                    "Simulation finished. Total requests={} Real time={}, simulation "
                                                            + "time={}",
                                                    total,
                                                    Duration.between(realStart, Instant.now()),
                                                    Duration.ofNanos(
                                                            simulation.clock().read()));

                                            // simulation.metrics().dumpCsv(Paths.get("./csv"));
                                            simulation.metrics().dumpPng(Paths.get("./par.png"));
                                        }

                                        return Futures.immediateFuture(null);
                                    },
                                    MoreExecutors.directExecutor());
                        }
                    },
                    batchNum * batchDelay.toNanos(),
                    TimeUnit.NANOSECONDS);
        }
    }

    private void fireOffSerialRequests(
            Simulation simulation, PreferLowestUpstreamUtilization thingWeAreTesting, int numRequests) {

        Runnable stopReporting = simulation.metrics().startReporting(Duration.ofSeconds(1));

        // fire off numRequests in a hot loop
        ListenableFuture<Integer> roundTrip = Futures.immediateFuture(0);
        for (int i = 0; i < numRequests; i++) {
            roundTrip = Futures.transformAsync(
                    roundTrip,
                    number -> {
                        Optional<Statistics.Upstream> best = thingWeAreTesting.getBest(endpoint);
                        assertThat(best).isPresent();

                        Statistics.Upstream upstream = best.get();
                        SimulationServer server = nodeToServer.get(upstream);
                        log.info(
                                "time={} request={} upstream={}",
                                simulation.clock().read(),
                                number,
                                server);

                        Statistics.InFlightStage inFlight = thingWeAreTesting.recordStart(upstream, endpoint, request);
                        ListenableFuture<Response> serverFuture = server.handleRequest(endpoint, request);
                        return Futures.transformAsync(
                                serverFuture,
                                resp -> {
                                    inFlight.recordComplete(resp, null);
                                    return Futures.immediateFuture(number + 1);
                                },
                                MoreExecutors.directExecutor());
                    },
                    MoreExecutors.directExecutor());
        }
        roundTrip.addListener(
                () -> {
                    stopReporting.run();
                    log.info(
                            "Simulation finished. Real time={}, simulation time={}",
                            Duration.between(realStart, Instant.now()),
                            Duration.ofNanos(simulation.clock().read()));

                    // simulation.metrics().dumpCsv(Paths.get("./csv"));
                    simulation.metrics().dumpPng(Paths.get("./metrics.png"));
                },
                MoreExecutors.directExecutor());
    }

    public static StatisticsImpl stats(Ticker clock, Statistics.Upstream... upstreams) {
        return new StatisticsImpl(() -> ImmutableList.copyOf(upstreams), SimulationUtils.DETERMINISTIC, clock);
    }

    private static Response response(int status, String version) {
        return SimulationUtils.response(status, version);
    }
}
