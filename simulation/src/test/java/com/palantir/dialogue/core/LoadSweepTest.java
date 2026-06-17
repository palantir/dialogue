/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Suppliers;
import com.palantir.dialogue.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds a server's saturation "knee" by sweeping the load against a resource-constrained server.
 *
 * <p>For each {@link Strategy} we emit three curves with offered load on the X axis (written to
 * {@code load_sweep.png}, raw numbers to {@code load_sweep.txt}):
 * <ol>
 *     <li>p99 client-perceived latency in <b>milliseconds</b>
 *     <li>goodput - successful (200) responses per second
 *     <li>success rate as a percentage
 * </ol>
 */
final class LoadSweepTest {
    private static final Logger log = LoggerFactory.getLogger(LoadSweepTest.class);

    /** In-flight requests a single node serves before its response time cliffs to 5x. */
    private static final int SLOWDOWN_CAPACITY_PER_NODE = 10;

    /** In-flight requests a single node serves before it starts shedding with 429s. */
    private static final int SHED_CAPACITY_PER_NODE = 20;

    private static final Duration BEST_CASE_RESPONSE = Duration.ofMillis(60);

    /** How long we offer load at each rate before letting the system drain. */
    private static final Duration WINDOW = Duration.ofSeconds(20);

    /** Safety net so an overloaded run can't hang forever draining its backlog. */
    private static final Duration ABORT_AFTER = Duration.ofMinutes(5);

    /** Offered request rates (req/s); chosen to bracket the ~2-node knee around 150-200 rps. */
    private static final int[] OFFERED_RPS = {25, 50, 75, 100, 125, 150, 175, 200, 250, 300, 400, 500};

    @Test
    void load_sweep() throws IOException {
        double[] offeredRps = toDoubles(OFFERED_RPS);

        Map<Strategy, double[]> p99LatencyMs = new LinkedHashMap<>();
        Map<Strategy, double[]> goodput = new LinkedHashMap<>();
        Map<Strategy, double[]> successPercent = new LinkedHashMap<>();

        StringBuilder table = new StringBuilder();
        table.append(String.format("%-40s %6s %10s %12s %9s%n", "strategy", "rps", "p99_ms", "goodput/s", "success%"));

        for (Strategy strategy : Strategy.values()) {
            double[] p99 = new double[OFFERED_RPS.length];
            double[] gp = new double[OFFERED_RPS.length];
            double[] success = new double[OFFERED_RPS.length];

            for (int i = 0; i < OFFERED_RPS.length; i++) {
                int rps = OFFERED_RPS[i];
                Benchmark.BenchmarkResult result = runOnce(strategy, rps);

                double p99Ms = result.clientHistogram().get99thPercentile() / 1_000_000d;
                double endSeconds = result.endTime().toNanos() / 1_000_000_000d;
                double good = endSeconds > 0 ? result.statusCodes().getOrDefault("200", 0) / endSeconds : 0d;
                double successPct = result.successPercentage();

                p99[i] = p99Ms;
                gp[i] = good;
                success[i] = successPct;

                assertThat(result.numReceived())
                        .as("strategy=%s rps=%s should receive responses", strategy, rps)
                        .isPositive();
                assertThat(successPct)
                        .as("strategy=%s rps=%s success%%", strategy, rps)
                        .isBetween(0d, 100d);

                table.append(String.format("%-40s %6d %10.1f %12.1f %9.1f%n", strategy, rps, p99Ms, good, successPct));
            }

            p99LatencyMs.put(strategy, p99);
            goodput.put(strategy, gp);
            successPercent.put(strategy, success);
            log.info("Swept {} across {} load levels", strategy, OFFERED_RPS.length);
        }

        // Client-perceived latency spans ~3 orders of magnitude (steady-state ms to backlog-queue minutes under
        // sustained overload), so the latency knee is only legible on a log Y axis.
        XYChart latencyChart =
                kneeChart("p99 client latency vs offered load", "p99 latency (ms)", offeredRps, p99LatencyMs, true);
        XYChart goodputChart = kneeChart("goodput vs offered load", "goodput (200s/sec)", offeredRps, goodput, false);
        XYChart successChart =
                kneeChart("success rate vs offered load", "success (%)", offeredRps, successPercent, false);

        String pngPath = "src/test/resources/load_sweep.png";
        SimulationMetricsReporter.png(pngPath, List.of(latencyChart, goodputChart, successChart));
        Files.write(
                Paths.get("src/test/resources/load_sweep.txt"), table.toString().getBytes(StandardCharsets.UTF_8));

        double[] unlimited = p99LatencyMs.get(Strategy.UNLIMITED_ROUND_ROBIN);
        assertThat(unlimited[unlimited.length - 1])
                .as("p99 latency at the highest offered load should exceed that at the lowest (the knee)")
                .isGreaterThan(unlimited[0]);
        assertThat(Paths.get(pngPath)).exists();
    }

    @SuppressWarnings("for-rollout:deprecation")
    private static Benchmark.BenchmarkResult runOnce(Strategy strategy, int rps) {
        Simulation simulation = new Simulation();
        Channel client = strategy.getChannel(simulation, twoNodes(simulation));
        return Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(rps)
                .sendUntil(WINDOW)
                .client(client)
                .abortAfter(ABORT_AFTER)
                .run();
    }

    private static Supplier<Map<String, SimulationServer>> twoNodes(Simulation simulation) {
        SimulationServer nodeA = node(simulation, "node-a");
        SimulationServer nodeB = node(simulation, "node-b");
        return Suppliers.memoize(() ->
                Stream.of(nodeA, nodeB).collect(Collectors.toMap(SimulationServer::toString, Function.identity())));
    }

    private static SimulationServer node(Simulation simulation, String name) {
        return SimulationServer.builder()
                .serverName(name)
                .simulation(simulation)
                .handler(h -> h.respond200UntilCapacity(429, SHED_CAPACITY_PER_NODE)
                        .linearResponseTime(BEST_CASE_RESPONSE, SLOWDOWN_CAPACITY_PER_NODE))
                .build();
    }

    private static XYChart kneeChart(
            String title,
            String yAxisTitle,
            double[] offeredRps,
            Map<Strategy, double[]> seriesByStrategy,
            boolean logarithmicYAxis) {
        XYChart chart = new XYChartBuilder()
                .width(1000)
                .height(420)
                .title(title)
                .xAxisTitle("offered load (req/s)")
                .yAxisTitle(yAxisTitle)
                .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        chart.getStyler().setMarkerSize(5);
        chart.getStyler().setYAxisLogarithmic(logarithmicYAxis);
        seriesByStrategy.forEach((strategy, values) -> chart.addSeries(strategy.name(), offeredRps, values));
        return chart;
    }

    private static double[] toDoubles(int[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }
}
