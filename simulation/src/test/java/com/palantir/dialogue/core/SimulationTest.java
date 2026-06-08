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

import static com.palantir.dialogue.core.Benchmark.DEFAULT_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Meter;
import com.google.common.base.Suppliers;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.Benchmark.ScheduledRequest;
import com.palantir.tracing.Observability;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.Tracers;
import java.io.IOException;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
@Execution(ExecutionMode.CONCURRENT)
final class SimulationTest {

    @SuppressWarnings("for-rollout:deprecation")
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @EnumSource(Strategy.class)
    @ParameterizedTest(
            name = ParameterizedTest.DISPLAY_NAME_PLACEHOLDER + "[" + ParameterizedTest.ARGUMENTS_PLACEHOLDER + "]")
    @interface SimulationCase {}

    private final Simulation simulation = new Simulation();
    private final SimulationReport report = new SimulationReport(simulation);
    private Outcome outcome;

    private record Outcome(Strategy strategy, Benchmark.BenchmarkResult result) {}

    @SimulationCase
    public void simplest_possible_case(Strategy strategy) {
        // real servers don't scale like this - see later tests
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("fast")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                server("medium")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(800)))
                        .build(),
                server("slightly_slow")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(1000)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(11)
                        .sendUntil(Duration.ofMinutes(20))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofHours(1))
                        .run());
    }

    @SimulationCase
    public void slowdown_and_error_thresholds(Strategy strategy) {
        Endpoint getEndpoint = SimulationUtils.endpoint("endpoint", HttpMethod.GET);
        int errorThreshold = 40;
        int slowdownThreshold = 30;
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("fast")
                        .handler(
                                getEndpoint,
                                h -> h.respond200UntilCapacity(500, errorThreshold)
                                        .linearResponseTime(Duration.ofMillis(600), slowdownThreshold))
                        .build(),
                server("medium")
                        .handler(
                                getEndpoint,
                                h -> h.respond200UntilCapacity(500, errorThreshold)
                                        .linearResponseTime(Duration.ofMillis(800), slowdownThreshold))
                        .build(),
                server("slightly_slow")
                        .handler(
                                getEndpoint,
                                h -> h.respond200UntilCapacity(500, errorThreshold)
                                        .linearResponseTime(Duration.ofMillis(1000), slowdownThreshold))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(500)
                        .sendUntil(Duration.ofSeconds(20))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .endpoints(getEndpoint)
                        .abortAfter(Duration.ofMinutes(10))
                        .run());
    }

    @SimulationCase
    public void slow_503s_then_revert(Strategy strategy) {
        int capacity = 60;
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("fast")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build(),
                server("slow_failures_then_revert")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .until(Duration.ofSeconds(3), "slow 503s")
                        .handler(h -> h.response(503).linearResponseTime(Duration.ofSeconds(1), capacity))
                        .until(Duration.ofSeconds(10), "revert")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(200)
                        .sendUntil(Duration.ofSeconds(
                                15)) // something weird happens at 1811... bug in DeterministicScheduler?
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofMinutes(10))
                        .run());
    }

    @SimulationCase
    public void fast_503s_then_revert(Strategy strategy) {
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("normal")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .build(),
                server("fast_503s_then_revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .until(Duration.ofSeconds(3), "fast 503s")
                        .handler(h -> h.response(503).responseTime(Duration.ofNanos(10)))
                        .until(Duration.ofMinutes(1), "revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(500)
                        .sendUntil(Duration.ofSeconds(90))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofMinutes(10))
                        .run());
    }

    @SimulationCase
    public void fast_400s_then_revert(Strategy strategy) {
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("normal")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .build(),
                server("fast_400s_then_revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .until(Duration.ofSeconds(3), "fast 400s")
                        .handler(h -> h.response(400).responseTime(Duration.ofMillis(20)))
                        .until(Duration.ofSeconds(30), "revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(100)
                        .sendUntil(Duration.ofMinutes(1))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofMinutes(10))
                        .run());
    }

    @SimulationCase
    public void short_outage_on_one_node(Strategy strategy) {
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("stable")
                        .handler(h -> h.response(200).responseTime(Duration.ofSeconds(2)))
                        .build(),
                server("has_short_outage")
                        .handler(h -> h.response(200).responseTime(Duration.ofSeconds(2)))
                        .until(Duration.ofSeconds(30), "20s_outage")
                        .handler(h -> h.response(500).responseTime(Duration.ofNanos(10)))
                        .until(Duration.ofSeconds(50), "revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofSeconds(2)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(20)
                        .sendUntil(Duration.ofSeconds(80))
                        .client(strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofMinutes(3))
                        .run());
    }

    @SimulationCase
    public void drastic_slowdown(Strategy strategy) {
        int capacity = 60;
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("fast")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build(),
                server("fast_then_slow_then_fast")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .until(Duration.ofSeconds(3), "slow 200s")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofSeconds(10), capacity))
                        .until(Duration.ofSeconds(10), "revert")
                        .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(60), capacity))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(200)
                        .sendUntil(Duration.ofSeconds(20))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofMinutes(10))
                        .run());
    }

    @SimulationCase
    public void all_nodes_500(Strategy strategy) {
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("node1")
                        .handler(h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(10), "revert badness")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                server("node2")
                        .handler(h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(10), "revert badness")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(100)
                        .sendUntil(Duration.ofSeconds(20))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofMinutes(10))
                        .run());
    }

    @SimulationCase
    public void black_hole(Strategy strategy) {
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("node1")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                server("node2_black_hole")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "black hole")
                        .handler(h -> h.response(200).responseTime(Duration.ofDays(1)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(200)
                        .sendUntil(Duration.ofSeconds(10))
                        .abortAfter(Duration.ofSeconds(30)) // otherwise the test never terminates!
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .run());
    }

    @SimulationCase
    public void one_endpoint_dies_on_each_server(Strategy strategy) {
        Endpoint endpoint1 = SimulationUtils.endpoint("e1", HttpMethod.POST);
        Endpoint endpoint2 = SimulationUtils.endpoint("e2", HttpMethod.POST);

        Supplier<Map<String, SimulationServer>> servers = servers(
                server("server_where_e1_breaks")
                        .handler(endpoint1, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "e1 breaks")
                        .handler(endpoint1, h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .build(),
                server("server_where_e2_breaks")
                        .handler(endpoint1, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .until(Duration.ofSeconds(3), "e2 breaks")
                        .handler(endpoint1, h -> h.response(200).responseTime(Duration.ofMillis(600)))
                        .handler(endpoint2, h -> h.response(500).responseTime(Duration.ofMillis(600)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(250)
                        .sendUntil(Duration.ofSeconds(10))
                        .abortAfter(Duration.ofMinutes(1))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .endpoints(endpoint1, endpoint2)
                        .abortAfter(Duration.ofMinutes(10))
                        .run());
    }

    @SimulationCase
    public void live_reloading(Strategy strategy) {
        int capacity = 60;
        Supplier<Map<String, SimulationServer>> servers = liveReloadingServers(
                beginAt(
                        Duration.ZERO,
                        server("always_on")
                                .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(600), capacity))
                                .build()),
                beginAt(
                        Duration.ZERO,
                        server("always_broken")
                                .handler(h -> h.response(500).linearResponseTime(Duration.ofMillis(600), capacity))
                                .build()),
                beginAt(
                        Duration.ofSeconds(5),
                        server("added_halfway")
                                .handler(h -> h.response(200).linearResponseTime(Duration.ofMillis(600), capacity))
                                .build()));

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(250)
                        .sendUntil(Duration.ofSeconds(10))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofMinutes(10))
                        .run());
    }

    @SimulationCase
    public void uncommon_flakes(Strategy strategy) {
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("fast0")
                        .handler(h -> h.response(respond500AtRate(.01D)).responseTime(Duration.ofNanos(1000)))
                        .build(),
                server("fast1")
                        .handler(h -> h.response(respond500AtRate(.01D)).responseTime(Duration.ofNanos(1000)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(1000)
                        .sendUntil(Duration.ofSeconds(10))
                        .clients(10, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofSeconds(10))
                        .run());
    }

    /**
     * This simulates an alta client, which might load up some keys and then lookup each key in order to build a big
     * response for the user. The goal is 100% client-perceived success here, because building up half the response
     * is no good.
     */
    @SimulationCase
    public void one_big_spike(Strategy strategy) {
        int capacity = 100;
        Supplier<Map<String, SimulationServer>> servers = servers(
                server("node1")
                        .handler(h -> h.respond200UntilCapacity(429, capacity).responseTime(Duration.ofMillis(150)))
                        .build(),
                server("node2")
                        .handler(h -> h.respond200UntilCapacity(429, capacity).responseTime(Duration.ofMillis(150)))
                        .build());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(30_000) // fire off a ton of requests very quickly
                        .numRequests(1000)
                        .client(strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofSeconds(10))
                        .run());
    }

    @SimulationCase
    void server_side_rate_limits(Strategy strategy) {
        double totalRateLimit = .1;
        int numServers = 4;
        int numClients = 2;
        double perServerRateLimit = totalRateLimit / numServers;

        Supplier<Map<String, SimulationServer>> servers = servers(IntStream.range(0, numServers)
                .mapToObj(i -> {
                    Meter requestRate = new Meter(simulation.codahaleClock());
                    Function<SimulationServer, Response> responseFunc = _s -> {
                        if (requestRate.getOneMinuteRate() < perServerRateLimit) {
                            requestRate.mark();
                            return new TestResponse().code(200);
                        } else {
                            return new TestResponse().code(429);
                        }
                    };
                    return server("node" + i)
                            .handler(h -> h.response(responseFunc).responseTime(Duration.ofSeconds(200)))
                            .build();
                })
                .toArray(SimulationServer[]::new));

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(totalRateLimit)
                        .sendUntil(Duration.ofMinutes(25_000))
                        .clients(numClients, _i -> strategy.getChannel(simulation, servers))
                        .abortAfter(Duration.ofHours(1_000))
                        .run());
    }

    @SimulationCase
    void server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client(Strategy strategy) {
        // 1 server
        // 2 types of clients sharing a DialogueChannel
        //   - client that sends a request once a second
        //   - client that burst sends 10k requests instantly
        // Assuming:
        //   * server concurrency limit of 1
        //   * 5ms to serve a request
        //
        // Serving the bursty client by itself would take 50s. That is fine for that client, because it
        // is trying to do a lot. However, we should not make the slow-and-steady client wait 50s to send it's request.
        int numServers = 1;
        int concurrencyLimit = 1;
        Duration responseTime = Duration.ofMillis(5);

        Duration benchmarkDuration = Duration.ofMinutes(1);

        Duration timeBetweenSlowAndSteadyRequests = Duration.ofSeconds(1);
        long numSlowAndSteady = benchmarkDuration.toNanos() / timeBetweenSlowAndSteadyRequests.toNanos();
        assertThat(numSlowAndSteady).isEqualTo(60);

        Duration timeBetweenBurstRequests = Duration.ofNanos(50);
        long numBurst = 10_000;

        long totalNumRequests = numSlowAndSteady + numBurst;
        assertThat(totalNumRequests).isEqualTo(10060);

        Supplier<Map<String, SimulationServer>> servers = servers(IntStream.range(0, numServers)
                .mapToObj(i -> server("node" + i)
                        .handler(h ->
                                h.respond200UntilCapacity(429, concurrencyLimit).responseTime(responseTime))
                        .build())
                .toArray(SimulationServer[]::new));

        Supplier<Channel> stickyChannelSupplier = strategy.getSticky2NonReloading(simulation, servers.get());

        Benchmark builder = Benchmark.builder().simulation(simulation);
        EndpointChannel slowAndSteadyChannel =
                builder.addEndpointChannel("slowAndSteady", DEFAULT_ENDPOINT, stickyChannelSupplier.get());
        EndpointChannel oneShotBurstChannel =
                builder.addEndpointChannel("oneShotBurst", DEFAULT_ENDPOINT, stickyChannelSupplier.get());

        Stream<ScheduledRequest> slowAndSteadyChannelRequests = builder.infiniteRequests(
                        timeBetweenSlowAndSteadyRequests, () -> slowAndSteadyChannel)
                .limit(numSlowAndSteady);

        Stream<ScheduledRequest> oneShotBurstChannelRequests = builder.infiniteRequests(
                        timeBetweenBurstRequests, () -> oneShotBurstChannel)
                .limit(numBurst);

        outcome = new Outcome(
                strategy,
                builder.mergeRequestStreams(slowAndSteadyChannelRequests, oneShotBurstChannelRequests)
                        .stopWhenNumReceived(totalNumRequests)
                        .abortAfter(benchmarkDuration.plus(Duration.ofMinutes(1)))
                        .run());
    }

    @SimulationCase
    void server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients(Strategy strategy) {
        int numServers = 1;
        int numClients = 10;
        Duration responseTime = Duration.ofMillis(150);
        int concurrencyLimit = 2;

        Supplier<Map<String, SimulationServer>> servers = servers(IntStream.range(0, numServers)
                .mapToObj(i -> server("node" + i)
                        .handler(h ->
                                h.respond200UntilCapacity(429, concurrencyLimit).responseTime(responseTime))
                        .build())
                .toArray(SimulationServer[]::new));

        Supplier<Channel> stickyChannelSupplier = strategy.getSticky2NonReloading(simulation, servers.get());

        outcome = new Outcome(
                strategy,
                Benchmark.builder()
                        .simulation(simulation)
                        .requestsPerSecond(30)
                        .sendUntil(Duration.ofMinutes(1))
                        .clients(numClients, _i -> stickyChannelSupplier.get())
                        .abortAfter(Duration.ofMinutes(2))
                        .run());
    }

    private Function<SimulationServer, Response> respond500AtRate(double rate) {
        Random random = new Random(4 /* Chosen by fair dice roll. Guaranteed to be random. */);
        return _server -> {
            if (random.nextDouble() <= rate) {
                return new TestResponse().code(500);
            }
            return new TestResponse().code(200);
        };
    }

    private SimulationServer.Builder server(String serverName) {
        return SimulationServer.builder().serverName(serverName).simulation(simulation);
    }

    private Supplier<Map<String, SimulationServer>> servers(SimulationServer... values) {
        return Suppliers.memoize(
                () -> Arrays.stream(values).collect(Collectors.toMap(SimulationServer::toString, Function.identity())));
    }

    /** Use the {@link #beginAt} method to simulate live-reloads. */
    private Supplier<Map<String, SimulationServer>> liveReloadingServers(
            Supplier<Optional<SimulationServer>>... serverSuppliers) {
        return () -> Arrays.stream(serverSuppliers)
                .map(Supplier::get)
                .<SimulationServer>mapMulti(Optional::ifPresent)
                .collect(Collectors.toMap(SimulationServer::toString, Function.identity()));
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

    @AfterEach
    public void after(TestInfo testInfo) throws IOException {
        report.record(testInfo, outcome.strategy(), outcome.result());

        assertThat(outcome.result().responsesLeaked())
                .describedAs("There should be no unclosed responses")
                .isZero();
    }

    @SuppressWarnings("for-rollout:deprecation")
    @BeforeEach
    public void before() {
        // purely a perf-optimization
        simulation.metricsReporter().onlyRecordMetricsFor(MetricNames::reportedMetricsPredicate);

        Tracer.setSampler(() -> false);
        Tracer.initTrace(Observability.DO_NOT_SAMPLE, Tracers.randomId());
    }

    @AfterAll
    public static void afterClass() throws IOException {
        SimulationReport.writeMarkdownReport();
    }
}
