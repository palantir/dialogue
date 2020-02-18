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
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
 * Simulates client heuristics defined in {@link Strategy} against {@link SimulationServer} nodes. These don't
 * actually bind to ports, they just schedule responses to return at some point. All scheduling happens on the
 * deterministic scheduler in {@link Simulation} (on the main thread), so hours of requests can be simulated instantly.
 *
 * These simulations only reveal characteristics and emergent behaviour of the clients - they can't be used to
 * compare how efficient (in terms of CPU or allocations) clients are - a dedicated microbenchmarking harness should
 * be used for this instead.
 *
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
        // real servers don't scale like this - see later tests
        servers = servers(
                SimulationServer.builder()
                        .serverName("fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                SimulationServer.builder()
                        .serverName("medium")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(800)))
                        .build(),
                SimulationServer.builder()
                        .serverName("slightly_slow")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(1000)))
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
    public void slowdown_and_error_thresholds() {
        int errorThreshold = 40;
        int slowdownThreshold = 30;
        servers = servers(
                SimulationServer.builder()
                        .serverName("fast")
                        .simulation(simulation)
                        .handler(h -> h.respond200UntilCapacity(500, errorThreshold)
                                .linearResponseTime(Duration.ofMillis(600), slowdownThreshold))
                        .build(),
                SimulationServer.builder()
                        .serverName("medium")
                        .simulation(simulation)
                        .handler(h -> h.respond200UntilCapacity(500, errorThreshold)
                                .linearResponseTime(Duration.ofMillis(800), slowdownThreshold))
                        .build(),
                SimulationServer.builder()
                        .serverName("slightly_slow")
                        .simulation(simulation)
                        .handler(h -> h.respond200UntilCapacity(500, errorThreshold)
                                .linearResponseTime(Duration.ofMillis(1000), slowdownThreshold))
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
                        .serverName("fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build(),
                SimulationServer.builder()
                        .serverName("slow_failures_then_revert")
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
                        .serverName("fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build(),
                SimulationServer.builder()
                        .serverName("fast_500s_then_revert")
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
                        .serverName("fast")
                        .simulation(simulation)
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build(),
                SimulationServer.builder()
                        .serverName("fast_then_slow_then_fast")
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
                        .serverName("node1")
                        .simulation(simulation)
                        .handler(h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(10), "revert badness")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                SimulationServer.builder()
                        .serverName("node2")
                        .simulation(simulation)
                        .handler(h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(10), "revert badness")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build());

        // TODO(dfox): seems like traceid=req-1-attempt-1 happens 31 times???

        Channel channel = strategy.getChannel(simulation, servers);
        result = Benchmark.builder()
                .requestsPerSecond(10)
                .sendUntil(Duration.ofSeconds(20))
                .channel(channel)
                .simulation(simulation)
                .run();
    }

    @Test
    public void black_hole() {
        servers = servers(
                SimulationServer.builder()
                        .serverName("node1")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                SimulationServer.builder()
                        .serverName("node2_black_hole")
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
                        .serverName("server_where_e1_breaks")
                        .simulation(simulation)
                        .handler(endpoint1, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "e1 breaks")
                        .handler(endpoint1, h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                SimulationServer.builder()
                        .serverName("server_where_e2_breaks")
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
                                .serverName("always_on")
                                .simulation(simulation)
                                .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(600), capacity))
                                .build()),
                beginAt(
                        Duration.ZERO,
                        SimulationServer.builder()
                                .serverName("always_broken")
                                .simulation(simulation)
                                .handler(h -> h.response(500).linearResponseTime(Duration.ofMillis(600), capacity))
                                .build()),
                beginAt(
                        Duration.ofSeconds(5),
                        SimulationServer.builder()
                                .serverName("added_halfway")
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

    private Supplier<List<SimulationServer>> servers(SimulationServer... values) {
        return Suppliers.memoize(() -> Arrays.asList(values));
    }

    /** Use the {@link #beginAt} method to simulate live-reloads. */
    private Supplier<List<SimulationServer>> liveReloadingServers(
            Supplier<Optional<SimulationServer>>... serverSuppliers) {
        return () -> Arrays.stream(serverSuppliers)
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
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
        Duration serverCpu = Duration.ofNanos(
                MetricNames.globalServerTimeNanos(simulation.taggedMetrics()).getCount());
        long clientMeanNanos = (long) result.clientHistogram().getMean();
        double clientMeanMillis = TimeUnit.MILLISECONDS.convert(clientMeanNanos, TimeUnit.NANOSECONDS);

        // intentionally using tabs so that opening report.txt with 'cat' aligns columns nicely
        String longSummary = String.format(
                "success=%s%%\tclient_mean=%-15s\tserver_cpu=%-15s\tclient_received=%s/%s\tserver_resps=%s\tcodes=%s",
                result.successPercentage(),
                Duration.of(clientMeanNanos, ChronoUnit.NANOS),
                serverCpu,
                result.numReceived(),
                result.numSent(),
                result.numGlobalResponses(),
                result.statusCodes());

        Path txt = Paths.get("src/test/resources/" + testName.getMethodName() + ".txt");
        String pngPath = "src/test/resources/" + testName.getMethodName() + ".png";
        String onDisk = Files.exists(txt) ? new String(Files.readAllBytes(txt), StandardCharsets.UTF_8) : "";

        boolean txtChanged = !longSummary.equals(onDisk);

        if (System.getenv().containsKey("CI")) { // only strict on CI, locally we just overwrite
            assertThat(onDisk)
                    .describedAs("Run tests locally to update checked-in file: %s", txt)
                    .isEqualTo(longSummary);
            assertThat(Paths.get(pngPath)).exists();
        } else if (txtChanged || !Files.exists(Paths.get(pngPath))) {
            // only re-generate PNGs if the txt file changed (as they're slow af)
            Stopwatch sw = Stopwatch.createStarted();
            Files.write(txt, longSummary.getBytes(StandardCharsets.UTF_8));

            XYChart activeRequests = simulation.metricsReporter().chart(Pattern.compile("active"));
            activeRequests.setTitle(String.format(
                    "%s success=%.0f%% client_mean=%.1f ms server_cpu=%s",
                    strategy, result.successPercentage(), clientMeanMillis, serverCpu));

            SimulationMetricsReporter.png(
                    pngPath, activeRequests, simulation.metricsReporter().chart(Pattern.compile("request.*count"))
                    // simulation.metrics().chart(Pattern.compile("(responseClose|globalResponses)"))
                    );
            log.info("Generated {} ({} ms)", pngPath, sw.elapsed(TimeUnit.MILLISECONDS));
        }

        assertThat(result.responsesLeaked())
                .describedAs("There should be no unclosed responses")
                .isZero();
    }

    @AfterClass
    public static void afterClass() throws IOException {
        // squish all txt files together into one markdown report so that github displays diffs
        try (Stream<Path> list = Files.list(Paths.get("src/test/resources"))) {
            List<Path> files = list.filter(p -> !p.toString().endsWith("report.md"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());

            String txtSection = files.stream()
                    .filter(p -> p.toString().endsWith("txt"))
                    .map(p -> {
                        try {
                            return String.format(
                                    "%70s:\t%s%n",
                                    p.getFileName().toString(),
                                    new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.joining("", "```\n", "```\n"));

            String images = files.stream()
                    .filter(p -> p.toString().endsWith("png"))
                    .map(p -> {
                        String githubLfsUrl = "https://media.githubusercontent.com/media/palantir/dialogue/develop/"
                                + "simulation/src/test/resources/"
                                + p.getFileName();
                        return String.format(
                                "%n## %s%n"
                                        + "<table><tr><th>develop</th><th>current</th></tr>%n"
                                        + "<tr>"
                                        + "<td><image width=400 src=\"%s\" /></td>"
                                        + "<td><image width=400 src=\"%s\" /></td>"
                                        + "</tr>"
                                        + "</table>%n%n",
                                p.getFileName(), githubLfsUrl, p.getFileName());
                    })
                    .collect(Collectors.joining());

            String report = String.format(
                    "# Report%n<!-- Run SimulationTest to regenerate this report. -->%n%s%n%n%s%n", txtSection, images);
            Files.write(Paths.get("src/test/resources/report.md"), report.getBytes(StandardCharsets.UTF_8));
        }
    }
}
