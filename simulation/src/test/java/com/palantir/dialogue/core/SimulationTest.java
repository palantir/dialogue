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

import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.knowm.xchart.XYChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * We have the following goals.
 * <ol>
 *     <li>Minimize user-perceived failures
 *     <li>Minimize user-perceived mean response time
 *     <li>Minimize total server CPU time spent
 * </ol>
 *
 * Heuristics should work sensibly for a variety of server response times (incl 1ms, 10ms, 100ms and 1s).
 * We usually have O(10) upstream nodes. Live-reloading node list shouldn't go crazy.
 *
 * The following scenarios are important for clients to handle.
 * <ol>
 *     <li>Normal operation: some node node is maybe 10-20% slower (e.g. maybe it's further away)
 *     <li>Fast failures (500/503/429) with revert: upgrading one node means everything gets insta 500'd (also 503 /
 *     429)
 *     <li>Slow failures (500/503/429) with revert: upgrading one node means all requests get slow and also return
 *     bad errors
 *     <li>Drastic slowdown with revert: One node suddenly starts taking 10 seconds to return (but not throwing errors)
 *     <li>All nodes return 500s briefly (ideally clients could queue up if they're willing to wait)
 *     <li>Black hole: one node just starts accepting requests but never returning responses
 * </ol>
 */
@RunWith(Parameterized.class)
public class SimulationTest {
    private static final Logger log = LoggerFactory.getLogger(SimulationTest.class);

    @Parameterized.Parameters(name = "{0}")
    public static Strategy[] data() {
        return Strategy.values();
    }

    @Parameterized.Parameter
    public Strategy strategy;

    @Rule
    public final TestName testName = new TestName();

    private final Simulation simulation = new Simulation();
    private Supplier<List<SimulationServer>> servers;
    private Benchmark.BenchmarkResult result;

    @Test
    public void simplest_possible_case() {
        int capacity = 20;
        servers = servers(
                SimulationServer.builder()
                        .metricName("fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(600), capacity))
                        .build(),
                SimulationServer.builder()
                        .metricName("medium")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(800), capacity))
                        .build(),
                SimulationServer.builder()
                        .metricName("slightly_slow")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(1000), capacity))
                        .build());

        Channel channel = strategy.getChannel(simulation, servers);
        result = Benchmark.builder()
                .requestsPerSecond(50)
                .sendUntil(Duration.ofSeconds(20))
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void slow_503s_then_revert() {
        int capacity = 60;
        servers = servers(
                SimulationServer.builder()
                        .metricName("fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build(),
                SimulationServer.builder()
                        .metricName("slow_failures_then_revert")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .until(Duration.ofSeconds(3), "slow 503s")
                        .handler(h -> h.response(503).linearResponseTime(Duration.ofSeconds(1), capacity))
                        .until(Duration.ofSeconds(10), "revert")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build());

        Channel channel = strategy.getChannel(simulation, servers);
        result = Benchmark.builder()
                .requestsPerSecond(200)
                .sendUntil(Duration.ofSeconds(15)) // something weird happens at 1811... bug in DeterministicScheduler?
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void fast_500s_then_revert() {
        int capacity = 60;
        servers = servers(
                SimulationServer.builder()
                        .metricName("fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build(),
                SimulationServer.builder()
                        .metricName("fast_500s_then_revert")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .until(Duration.ofSeconds(3), "fast 500s")
                        .handler(h -> h.response(500).linearResponseTime(Duration.ofMillis(10), capacity))
                        .until(Duration.ofSeconds(10), "revert")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build());

        Channel channel = strategy.getChannel(simulation, servers);
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
        servers = servers(
                SimulationServer.builder()
                        .metricName("fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build(),
                SimulationServer.builder()
                        .metricName("fast_then_slow_then_fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .until(Duration.ofSeconds(3), "slow 200s")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofSeconds(10), capacity))
                        .until(Duration.ofSeconds(10), "revert")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build());

        Channel channel = strategy.getChannel(simulation, servers);
        result = Benchmark.builder()
                .requestsPerSecond(200)
                .sendUntil(Duration.ofSeconds(20))
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void all_nodes_500() {
        servers = servers(
                SimulationServer.builder()
                        .metricName("node1")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "500s (same speed)")
                        .handler(h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(10), "revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                SimulationServer.builder()
                        .metricName("node2")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "500s (same speed)")
                        .handler(h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(10), "revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build());

        Channel channel = strategy.getChannel(simulation, servers);
        result = Benchmark.builder()
                .requestsPerSecond(20)
                .sendUntil(Duration.ofSeconds(20))
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void black_hole() {
        servers = servers(
                SimulationServer.builder()
                        .metricName("node1")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                SimulationServer.builder()
                        .metricName("node2_black_hole")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "black hole")
                        .handler(h -> h.response(200).responseTime(Duration.ofDays(1)))
                        .build());

        Channel channel = strategy.getChannel(simulation, servers);
        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(20)
                .sendUntil(Duration.ofSeconds(10))
                .abortAfter(Duration.ofSeconds(30)) // otherwise the test never terminates!
                .channel(channel)
                .run();
    }

    @Test
    public void one_endpoint_dies_on_each_server() {
        Endpoint endpoint1 = SimulationUtils.endpoint("e1");
        Endpoint endpoint2 = SimulationUtils.endpoint("e2");

        servers = servers(
                SimulationServer.builder()
                        .metricName("server_where_e1_breaks")
                        .simulation(simulation)
                        .handler(endpoint1, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "e1 breaks")
                        .handler(endpoint1, h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                SimulationServer.builder()
                        .metricName("server_where_e2_breaks")
                        .simulation(simulation)
                        .handler(endpoint1, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "e2 breaks")
                        .handler(endpoint1, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .build());

        Channel channel = strategy.getChannel(simulation, servers);
        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(51)
                .sendUntil(Duration.ofSeconds(10))
                .randomEndpoints(endpoint1, endpoint2)
                .abortAfter(Duration.ofMinutes(1))
                .channel(channel)
                .run();
    }

    @Test
    public void live_reloading() {
        int capacity = 60;
        servers = liveReloadingServers(
                beginAt(
                        Duration.ZERO,
                        SimulationServer.builder()
                                .metricName("always_on")
                                .simulation(simulation)
                                .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(600), capacity))
                                .build()),
                beginAt(
                        Duration.ZERO,
                        SimulationServer.builder()
                                .metricName("always_broken")
                                .simulation(simulation)
                                .handler(h -> h.response(500).linearResponseTime(Duration.ofMillis(600), capacity))
                                .build()),
                beginAt(
                        Duration.ofSeconds(5),
                        SimulationServer.builder()
                                .metricName("added_halfway")
                                .simulation(simulation)
                                .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(600), capacity))
                                .build()));

        Channel channel = strategy.getChannel(simulation, servers);
        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(40)
                .sendUntil(Duration.ofSeconds(10))
                .channel(channel)
                .run();
    }

    private Supplier<List<SimulationServer>> servers(SimulationServer... s) {
        return Suppliers.memoize(() -> Arrays.asList(s));
    }

    /** Use the {@link #beginAt} method to simulate live-reloads. */
    private Supplier<List<SimulationServer>> liveReloadingServers(
            Supplier<Optional<SimulationServer>>... serverSuppliers) {
        return () -> {
            List<SimulationServer> simulationServers = Arrays.stream(serverSuppliers)
                    .map(Supplier::get)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
            return simulationServers;
        };
    }

    private Supplier<Optional<SimulationServer>> beginAt(Duration beginTime, SimulationServer server) {
        boolean[] enabled = {false};
        return () -> {
            if (simulation.clock().read() >= beginTime.toNanos()) {
                if (!enabled[0]) {
                    enabled[0] = true;
                    simulation.events().event("new server: " + server);
                }
                return Optional.of(server);
            } else {
                return Optional.empty();
            }
        };
    }

    @After
    public void after() throws IOException {
        Duration serverCpu = Duration.ofNanos(servers.get().stream() // live-reloading breaks this :(
                .mapToLong(s -> s.getCumulativeServerTime().toNanos())
                .sum());
        long clientMeanNanos = (long) result.clientHistogram().getMean();
        double clientMeanMillis = TimeUnit.MICROSECONDS.convert(clientMeanNanos, TimeUnit.NANOSECONDS) / 1000d;

        // intentionally using tabs so that opening report.txt with 'cat' aligns columns nicely
        String longSummary = String.format(
                "success=%s%%\tclient_mean=%-15s\tserver_cpu=%-15s\treceived=%s/%s\tcodes=%s",
                result.successPercentage(),
                Duration.ofNanos(clientMeanNanos),
                serverCpu,
                result.numReceived(),
                result.numSent(),
                result.statusCodes());

        Path txt = Paths.get("src/test/resources/" + testName.getMethodName() + ".txt");
        String pngPath = "src/test/resources/" + testName.getMethodName() + ".png";
        String onDisk = Files.exists(txt) ? new String(Files.readAllBytes(txt), StandardCharsets.UTF_8) : "";
        boolean txtChanged = !longSummary.equals(onDisk);

        if (txtChanged || !Files.exists(Paths.get(pngPath))) {
            // only re-generate PNGs if the txt file changed (as they're slow af)
            Stopwatch sw = Stopwatch.createStarted();
            Files.write(txt, longSummary.getBytes(StandardCharsets.UTF_8));

            XYChart activeRequests = simulation.metrics().chart(Pattern.compile("active"));
            activeRequests.setTitle(String.format(
                    "%s success=%.0f%% client_mean=%.1f ms server_cpu=%s",
                    strategy, result.successPercentage(), clientMeanMillis, serverCpu));
            XYChart serverRequestCount = simulation.metrics().chart(Pattern.compile("request.*count"));
            XYChart clientStuff = simulation.metrics().chart(Pattern.compile("(refusals|starts).count"));

            SimulationMetrics.png(pngPath, activeRequests, serverRequestCount, clientStuff);
            log.info("Generated {} ({} ms)", pngPath, sw.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    @AfterClass
    public static void afterClass() throws IOException {
        // squish all txt files together into one report to make it easier to compare during code review
        String report = Files.list(Paths.get("src/test/resources"))
                .filter(p -> p.toString().endsWith(".txt") && !p.toString().endsWith("report.txt"))
                .map(p -> {
                    try {
                        return String.format(
                                "%70s:\t%s%n",
                                p.getFileName().toString(), new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .sorted(Comparator.comparing(String::trim))
                .collect(Collectors.joining());
        Files.write(Paths.get("src/test/resources/report.txt"), report.getBytes(StandardCharsets.UTF_8));
    }
}
