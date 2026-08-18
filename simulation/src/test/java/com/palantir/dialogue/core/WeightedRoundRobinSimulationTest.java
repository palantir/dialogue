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

import com.palantir.dialogue.core.Benchmark.BenchmarkResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.XYChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Studies the server-reported-utilization {@link Strategy#WEIGHTED_ROUND_ROBIN} strategy against the count-balancing
 * {@link Strategy#CONCURRENCY_LIMITER_ROUND_ROBIN} baseline. The mock {@link SimulationServer}s advertise a utilization
 * via the {@code X-Witchcraft-Utilization} header (see {@link SimulationServer.ServerHandler#reportUtilization} and
 * {@link SimulationServer.ServerHandler#utilizationFromCapacity}) — only the number is mocked; the real client parsing
 * and selection code runs.
 *
 * <p>Unlike {@link SimulationTest}, these assert semantic outcomes (WRR beats the baseline; the split tracks the
 * reported load) rather than golden output, since the point is to study behaviour under a range of reported values.
 * PNGs are written locally as study artifacts.
 */
final class WeightedRoundRobinSimulationTest {
    private static final Logger log = LoggerFactory.getLogger(WeightedRoundRobinSimulationTest.class);

    private static final Duration FAST = Duration.ofMillis(20);
    private static final Duration BEST_CASE = Duration.ofMillis(60);
    // Runs cold-start, so every node spends its first ~10s in the A58 blackout serving the peer average (uniform).
    // Measure over a window long enough that the blackout is a small fraction; in production, connections are
    // long-lived so the blackout is negligible.
    private static final Duration STEADY_WINDOW = Duration.ofMinutes(2);

    private static final StringBuilder REPORT = new StringBuilder();

    /**
     * Open-loop: two nodes advertise fixed utilizations regardless of load. WRR should route proportionally to the
     * (inverse) reported load, while the count-balancing baseline ignores it and splits evenly.
     */
    @Test
    void routes_by_reported_utilization_not_count() {
        Function<Simulation, Map<String, SimulationServer>> topology =
                sim -> serversMap(fixedUtilizationNode(sim, "idle", 0.2), fixedUtilizationNode(sim, "busy", 0.8));

        Run balanced = run(Strategy.CONCURRENCY_LIMITER_ROUND_ROBIN, topology, 250, 4, STEADY_WINDOW);
        double balancedIdleShare = share(balanced, "idle");
        log.info("baseline idle share={} (expected ~0.5, count-balanced ignores utilization)", balancedIdleShare);
        assertThat(balancedIdleShare)
                .as("count-balancing ignores utilization, so the split is roughly even")
                .isBetween(0.4, 0.6);

        Run wrr = run(Strategy.WEIGHTED_ROUND_ROBIN, topology, 250, 4, STEADY_WINDOW);
        double wrrIdleShare = share(wrr, "idle");
        log.info("wrr idle share={} (expected skewed toward the idle node)", wrrIdleShare);
        assertThat(wrrIdleShare)
                .as("WRR routes proportionally away from the busier (higher-utilization) node")
                .isGreaterThan(0.65);

        // PIN_UNTIL_ERROR ignores utilization too, but each client sticks to one node until it errors; since neither
        // node errors here, the split just reflects how the clients happened to pin.
        double pinIdleShare =
                share(run(Strategy.CONCURRENCY_LIMITER_PIN_UNTIL_ERROR, topology, 250, 4, STEADY_WINDOW), "idle");

        REPORT.append("== fixed utilization: idle=0.2 busy=0.8 (share of traffic to the idle node) ==\n");
        REPORT.append(String.format(Locale.ROOT, "  baseline (count)  idle_share=%.3f%n", balancedIdleShare));
        REPORT.append(String.format(Locale.ROOT, "  pin-until-error   idle_share=%.3f%n", pinIdleShare));
        REPORT.append(String.format(Locale.ROOT, "  weighted (util)   idle_share=%.3f%n%n", wrrIdleShare));
    }

    /**
     * The case where a server-reported load signal genuinely beats count-balancing: a degraded node that is highly
     * utilized but does <em>not</em> error (so the failure-driven concurrency limiter stays blind to it), seen by many
     * <em>sparse</em> clients whose tiny per-client in-flight cannot reveal the node's global load. Count-balancing
     * keeps sending it a fair share and eats its slow responses; WRR reads its high utilization and routes away, so
     * the fleet's tail latency is much lower. (An erroring degraded node is already handled by the concurrency
     * limiter, so WRR adds little there — see the class doc / study notes.)
     */
    @Test
    void routes_around_a_slow_node_that_the_limiter_cannot_see() throws IOException {
        Function<Simulation, Map<String, SimulationServer>> topology = sim -> serversMap(
                slowNode(sim, "healthy-0", 50),
                slowNode(sim, "healthy-1", 50),
                slowNode(sim, "healthy-2", 50),
                slowNode(sim, "degraded", 5));

        Run balanced = run(Strategy.CONCURRENCY_LIMITER_ROUND_ROBIN, topology, 600, 100, Duration.ofSeconds(90));
        Run pin = run(Strategy.CONCURRENCY_LIMITER_PIN_UNTIL_ERROR, topology, 600, 100, Duration.ofSeconds(90));
        Run wrr = run(Strategy.WEIGHTED_ROUND_ROBIN, topology, 600, 100, Duration.ofSeconds(90));

        REPORT.append("== slow (non-erroring) degraded node, sparse clients "
                + "(3 healthy slowdown=50, 1 degraded slowdown=5; 600 rps, 100 clients) ==\n");
        REPORT.append(summaryLine("baseline (count)  ", balanced));
        REPORT.append(summaryLine("pin-until-error   ", pin));
        REPORT.append(summaryLine("weighted (util)   ", wrr));

        assertThat(share(wrr, "degraded"))
                .as("WRR should send the slow node a smaller share than count-balancing does")
                .isLessThan(share(balanced, "degraded"));
        // The slow node's latency cliffs to a flat 5x, so any share above ~1% pins p99; mean latency is what tracks
        // the fraction of traffic sent to the slow node.
        assertThat(meanMillis(wrr))
                .as("routing away from the slow node should lower WRR's mean latency")
                .isLessThan(meanMillis(balanced));

        writeActiveRequestChart("slow_node_baseline", balanced);
        writeActiveRequestChart("slow_node_pin_until_error", pin);
        writeActiveRequestChart("slow_node_wrr", wrr);
    }

    /**
     * Study: sweep one node's reported utilization while the other stays fixed, and record how WRR splits traffic.
     * This is the "how do clients respond to various reported values" table. The baseline row shows count-balancing
     * is flat regardless of reported load.
     */
    @Test
    void utilization_sweep_shifts_traffic_monotonically() {
        double fixedUtil = 0.5;
        double[] sweptUtils = {0.1, 0.3, 0.5, 0.7, 0.9};

        StringBuilder table = new StringBuilder(
                String.format(Locale.ROOT, "%n%-37s %8s %14s%n", "strategy", "b_util", "a_share(fixed=0.5)"));
        List<Double> wrrAShares = new ArrayList<>();
        for (double bUtil : sweptUtils) {
            Function<Simulation, Map<String, SimulationServer>> topology =
                    sim -> serversMap(fixedUtilizationNode(sim, "a", fixedUtil), fixedUtilizationNode(sim, "b", bUtil));

            double wrrAShare = share(run(Strategy.WEIGHTED_ROUND_ROBIN, topology, 200, 4, STEADY_WINDOW), "a");
            double baselineAShare =
                    share(run(Strategy.CONCURRENCY_LIMITER_ROUND_ROBIN, topology, 200, 4, STEADY_WINDOW), "a");
            double pinAShare =
                    share(run(Strategy.CONCURRENCY_LIMITER_PIN_UNTIL_ERROR, topology, 200, 4, STEADY_WINDOW), "a");
            wrrAShares.add(wrrAShare);
            table.append(String.format(Locale.ROOT, "%-37s %8.2f %14.3f%n", "WEIGHTED_ROUND_ROBIN", bUtil, wrrAShare));
            table.append(String.format(
                    Locale.ROOT, "%-37s %8.2f %14.3f%n", "CONCURRENCY_LIMITER_ROUND_ROBIN", bUtil, baselineAShare));
            table.append(String.format(
                    Locale.ROOT, "%-37s %8.2f %14.3f%n", "CONCURRENCY_LIMITER_PIN_UNTIL_ERROR", bUtil, pinAShare));
        }
        log.info("utilization sweep (node a fixed at {}):{}", fixedUtil, table);
        REPORT.append("== utilization sweep (node a fixed at 0.5, node b swept) ==")
                .append(table)
                .append('\n');

        for (int i = 1; i < wrrAShares.size(); i++) {
            assertThat(wrrAShares.get(i))
                    .as("as node b's reported utilization rises, WRR should send node a a larger share")
                    .isGreaterThanOrEqualTo(wrrAShares.get(i - 1));
        }
        assertThat(wrrAShares.get(wrrAShares.size() - 1))
                .as("when b is much busier than a, a should get the majority of traffic")
                .isGreaterThan(0.6);
    }

    /**
     * Shock: a node's reported utilization jumps from idle to busy partway through the run. WRR should re-settle onto
     * the change — routing less traffic to the node afterward — and, thanks to the average fallback and blackout, do
     * so without oscillating back. The PNG shows the re-balancing over time; the assertion compares against a control
     * where the node stays idle. (Expiry itself doesn't fire here: weighted-random keeps every node getting some
     * traffic, so readings never go stale — expiry is covered by the unit test.)
     */
    @Test
    void re_settles_when_a_nodes_reported_load_changes_mid_run() throws IOException {
        Duration shiftAt = Duration.ofMinutes(1);

        double shareIfItStaysIdle = share(
                run(
                        Strategy.WEIGHTED_ROUND_ROBIN,
                        sim -> serversMap(
                                fixedUtilizationNode(sim, "steady", 0.2), fixedUtilizationNode(sim, "shifter", 0.2)),
                        250,
                        8,
                        STEADY_WINDOW),
                "shifter");

        Run shock = run(
                Strategy.WEIGHTED_ROUND_ROBIN,
                sim -> serversMap(
                        fixedUtilizationNode(sim, "steady", 0.2),
                        shiftingUtilizationNode(sim, "shifter", 0.2, 0.9, shiftAt)),
                250,
                8,
                STEADY_WINDOW);
        double shareWhenItGoesBusy = share(shock, "shifter");

        REPORT.append("== reported-load shock (shifter 0.2 -> 0.9 at 60s; steady 0.2; 250 rps, 8 clients, 120s) ==\n");
        REPORT.append(String.format(Locale.ROOT, "  shifter share if it stays idle  = %.3f%n", shareIfItStaysIdle));
        REPORT.append(String.format(Locale.ROOT, "  shifter share when it goes busy = %.3f%n%n", shareWhenItGoesBusy));

        assertThat(shareWhenItGoesBusy)
                .as("after the shifter reports higher load mid-run, WRR should route less traffic to it")
                .isLessThan(shareIfItStaysIdle);

        writeActiveRequestChart("reported_load_shock", shock);
    }

    private static SimulationServer fixedUtilizationNode(Simulation sim, String name, double utilization) {
        return SimulationServer.builder()
                .serverName(name)
                .simulation(sim)
                .handler(h -> h.response(200).responseTime(FAST).reportUtilization(utilization))
                .build();
    }

    /** Reports {@code before} until {@code cutover}, then {@code after} — used to shock the reported load mid-run. */
    private static SimulationServer shiftingUtilizationNode(
            Simulation sim, String name, double before, double after, Duration cutover) {
        return SimulationServer.builder()
                .serverName(name)
                .simulation(sim)
                .handler(h -> h.response(200).responseTime(FAST).reportUtilization(before))
                .until(cutover, name + " utilization " + before + " -> " + after)
                .handler(h -> h.response(200).responseTime(FAST).reportUtilization(after))
                .build();
    }

    /**
     * A node that always responds 200 but whose latency grows with in-flight load past {@code slowdownCapacity}, and
     * which advertises utilization derived from that same capacity. A low capacity models a degraded/less-provisioned
     * node that slows down without ever returning an error.
     */
    private static SimulationServer slowNode(Simulation sim, String name, int slowdownCapacity) {
        return SimulationServer.builder()
                .serverName(name)
                .simulation(sim)
                .handler(h -> h.response(200)
                        .linearResponseTime(BEST_CASE, slowdownCapacity)
                        .utilizationFromCapacity(slowdownCapacity))
                .build();
    }

    private static Run run(
            Strategy strategy,
            Function<Simulation, Map<String, SimulationServer>> topology,
            int requestsPerSecond,
            int clients,
            Duration duration) {
        Simulation simulation = new Simulation();
        Map<String, SimulationServer> servers = topology.apply(simulation);
        Supplier<Map<String, SimulationServer>> supplier = () -> servers;
        BenchmarkResult result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(requestsPerSecond)
                .sendUntil(duration)
                .clients(clients, _i -> strategy.getChannel(simulation, supplier))
                .abortAfter(duration.plusMinutes(5))
                .run();
        return new Run(simulation, servers, result);
    }

    private static Map<String, SimulationServer> serversMap(SimulationServer... values) {
        return Arrays.stream(values).collect(Collectors.toMap(SimulationServer::toString, Function.identity()));
    }

    private static long requestCount(Run run, String serverName) {
        return MetricNames.requestMeter(run.simulation.taggedMetrics(), serverName, Benchmark.DEFAULT_ENDPOINT)
                .getCount();
    }

    private static double share(Run run, String serverName) {
        long total = run.servers.keySet().stream()
                .mapToLong(name -> requestCount(run, name))
                .sum();
        return total == 0 ? 0 : (double) requestCount(run, serverName) / total;
    }

    private static double p99Millis(Run run) {
        return run.result.clientHistogram().get99thPercentile() / 1_000_000d;
    }

    private static double meanMillis(Run run) {
        return run.result.clientHistogram().getMean() / 1_000_000d;
    }

    private static String summaryLine(String label, Run run) {
        return String.format(
                Locale.ROOT,
                "  %s success=%5.1f%%  mean=%6.1fms  p99=%6.0fms  serverResponses=%7d  degradedShare=%.3f%n",
                label,
                run.result.successPercentage(),
                meanMillis(run),
                p99Millis(run),
                run.result.numGlobalResponses(),
                share(run, "degraded"));
    }

    @AfterAll
    static void writeReport() throws IOException {
        if (System.getenv().containsKey("CI") || REPORT.length() == 0) {
            return;
        }
        Files.writeString(Paths.get("src/test/resources/txt/wrr_study.txt"), REPORT.toString(), StandardCharsets.UTF_8);
    }

    private static void writeActiveRequestChart(String name, Run run) throws IOException {
        if (System.getenv().containsKey("CI")) {
            return;
        }
        try {
            XYChart chart = run.simulation.metricsReporter().chart(MetricNames.serverActiveRequestsPattern());
            chart.setTitle(String.format(Locale.ROOT, "%s success=%.1f%%", name, run.result.successPercentage()));
            SimulationMetricsReporter.png("src/test/resources/" + name + ".png", List.of(chart));
        } catch (RuntimeException e) {
            log.warn("Failed to render study chart {}", name, e);
        }
    }

    private static final class Run {
        private final Simulation simulation;
        private final Map<String, SimulationServer> servers;
        private final BenchmarkResult result;

        Run(Simulation simulation, Map<String, SimulationServer> servers, BenchmarkResult result) {
            this.simulation = simulation;
            this.servers = servers;
            this.result = result;
        }
    }
}
