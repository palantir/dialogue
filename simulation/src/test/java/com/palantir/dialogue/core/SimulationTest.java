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
import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@Execution(ExecutionMode.CONCURRENT)
final class SimulationTest {
    private static final Logger log = LoggerFactory.getLogger(SimulationTest.class);

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @EnumSource(Strategy.class)
    @ParameterizedTest(
            name = ParameterizedTest.DISPLAY_NAME_PLACEHOLDER + "[" + ParameterizedTest.ARGUMENTS_PLACEHOLDER + "]")
    @interface SimulationCase {}

    private final Simulation simulation = new Simulation();
    private Supplier<Map<String, SimulationServer>> servers;
    private Benchmark.BenchmarkResult result;

    @SimulationCase
    public void simplest_possible_case(Strategy strategy) {
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

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(11)
                .sendUntil(Duration.ofMinutes(20))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofHours(1))
                .run();
    }

    @SimulationCase
    public void slowdown_and_error_thresholds(Strategy strategy) {
        Endpoint getEndpoint = SimulationUtils.endpoint("endpoint", HttpMethod.GET);
        int errorThreshold = 40;
        int slowdownThreshold = 30;
        servers = servers(
                SimulationServer.builder()
                        .serverName("fast")
                        .simulation(simulation)
                        .handler(getEndpoint, h -> h.respond200UntilCapacity(500, errorThreshold)
                                .linearResponseTime(Duration.ofMillis(600), slowdownThreshold))
                        .build(),
                SimulationServer.builder()
                        .serverName("medium")
                        .simulation(simulation)
                        .handler(getEndpoint, h -> h.respond200UntilCapacity(500, errorThreshold)
                                .linearResponseTime(Duration.ofMillis(800), slowdownThreshold))
                        .build(),
                SimulationServer.builder()
                        .serverName("slightly_slow")
                        .simulation(simulation)
                        .handler(getEndpoint, h -> h.respond200UntilCapacity(500, errorThreshold)
                                .linearResponseTime(Duration.ofMillis(1000), slowdownThreshold))
                        .build());

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(500)
                .sendUntil(Duration.ofSeconds(20))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .endpoints(getEndpoint)
                .abortAfter(Duration.ofMinutes(10))
                .run();
    }

    @SimulationCase
    public void slow_503s_then_revert(Strategy strategy) {
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

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(200)
                .sendUntil(Duration.ofSeconds(15)) // something weird happens at 1811... bug in DeterministicScheduler?
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofMinutes(10))
                .run();
    }

    @SimulationCase
    public void fast_503s_then_revert(Strategy strategy) {
        servers = servers(
                SimulationServer.builder()
                        .serverName("normal")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .build(),
                SimulationServer.builder()
                        .serverName("fast_503s_then_revert")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .until(Duration.ofSeconds(3), "fast 503s")
                        .handler(h -> h.response(503).responseTime(Duration.ofNanos(10)))
                        .until(Duration.ofMinutes(1), "revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .build());

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(500)
                .sendUntil(Duration.ofSeconds(90))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofMinutes(10))
                .run();
    }

    @SimulationCase
    public void fast_400s_then_revert(Strategy strategy) {
        servers = servers(
                SimulationServer.builder()
                        .serverName("normal")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .build(),
                SimulationServer.builder()
                        .serverName("fast_400s_then_revert")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .until(Duration.ofSeconds(3), "fast 400s")
                        .handler(h -> h.response(400).responseTime(Duration.ofMillis(20)))
                        .until(Duration.ofSeconds(30), "revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofMillis(120)))
                        .build());

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(100)
                .sendUntil(Duration.ofMinutes(1))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofMinutes(10))
                .run();
    }

    @SimulationCase
    public void short_outage_on_one_node(Strategy strategy) {
        servers = servers(
                SimulationServer.builder()
                        .serverName("stable")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofSeconds(2)))
                        .build(),
                SimulationServer.builder()
                        .serverName("has_short_outage")
                        .simulation(simulation)
                        .handler(h -> h.response(200).responseTime(Duration.ofSeconds(2)))
                        .until(Duration.ofSeconds(30), "20s_outage")
                        .handler(h -> h.response(500).responseTime(Duration.ofNanos(10)))
                        .until(Duration.ofSeconds(50), "revert")
                        .handler(h -> h.response(200).responseTime(Duration.ofSeconds(2)))
                        .build());

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(20)
                .sendUntil(Duration.ofSeconds(80))
                .client(strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofMinutes(3))
                .run();
    }

    @SimulationCase
    public void drastic_slowdown(Strategy strategy) {
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

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(200)
                .sendUntil(Duration.ofSeconds(20))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofMinutes(10))
                .run();
    }

    @SimulationCase
    public void all_nodes_500(Strategy strategy) {
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

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(100)
                .sendUntil(Duration.ofSeconds(20))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofMinutes(10))
                .run();
    }

    @SimulationCase
    public void black_hole(Strategy strategy) {
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

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(200)
                .sendUntil(Duration.ofSeconds(10))
                .abortAfter(Duration.ofSeconds(30)) // otherwise the test never terminates!
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .run();
    }

    @SimulationCase
    public void one_endpoint_dies_on_each_server(Strategy strategy) {
        Endpoint endpoint1 = SimulationUtils.endpoint("e1", HttpMethod.POST);
        Endpoint endpoint2 = SimulationUtils.endpoint("e2", HttpMethod.POST);

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

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(250)
                .sendUntil(Duration.ofSeconds(10))
                .abortAfter(Duration.ofMinutes(1))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .endpoints(endpoint1, endpoint2)
                .abortAfter(Duration.ofMinutes(10))
                .run();
    }

    @SimulationCase
    public void live_reloading(Strategy strategy) {
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

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(250)
                .sendUntil(Duration.ofSeconds(10))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofMinutes(10))
                .run();
    }

    @SimulationCase
    public void uncommon_flakes(Strategy strategy) {
        servers = servers(
                SimulationServer.builder()
                        .serverName("fast0")
                        .simulation(simulation)
                        .handler(h -> h.response(respond500AtRate(.01D)).responseTime(Duration.ofNanos(1000)))
                        .build(),
                SimulationServer.builder()
                        .serverName("fast1")
                        .simulation(simulation)
                        .handler(h -> h.response(respond500AtRate(.01D)).responseTime(Duration.ofNanos(1000)))
                        .build());

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(1000)
                .sendUntil(Duration.ofSeconds(10))
                .clients(10, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofSeconds(10))
                .run();
    }

    /**
     * This simulates an alta client, which might load up some keys and then lookup each key in order to build a big
     * response for the user. The goal is 100% client-perceived success here, because building up half the response
     * is no good.
     */
    @SimulationCase
    public void one_big_spike(Strategy strategy) {
        int capacity = 100;
        servers = servers(
                SimulationServer.builder()
                        .serverName("node1")
                        .simulation(simulation)
                        .handler(h -> h.respond200UntilCapacity(429, capacity).responseTime(Duration.ofMillis(150)))
                        .build(),
                SimulationServer.builder()
                        .serverName("node2")
                        .simulation(simulation)
                        .handler(h -> h.respond200UntilCapacity(429, capacity).responseTime(Duration.ofMillis(150)))
                        .build());

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(30_000) // fire off a ton of requests very quickly
                .numRequests(1000)
                .client(strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofSeconds(10))
                .run();
    }

    @SimulationCase
    void server_side_rate_limits(Strategy strategy) {
        double totalRateLimit = .1;
        int numServers = 4;
        int numClients = 2;
        double perServerRateLimit = totalRateLimit / numServers;

        servers = servers(IntStream.range(0, numServers)
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
                    return SimulationServer.builder()
                            .serverName("node" + i)
                            .simulation(simulation)
                            .handler(h -> h.response(responseFunc).responseTime(Duration.ofSeconds(200)))
                            .build();
                })
                .toArray(SimulationServer[]::new));

        result = Benchmark.builder()
                .simulation(simulation)
                .requestsPerSecond(totalRateLimit)
                .sendUntil(Duration.ofMinutes(25_000))
                .clients(numClients, _i -> strategy.getChannel(simulation, servers))
                .abortAfter(Duration.ofHours(1_000))
                .run();
    }

    @Test
    void server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client() {

        // 1 server
        // 2 types of clients sharing a DialogueChannel
        //   - client that sends a request once a second
        //   - client that burst sends 10k requests instantly
        // Assuming:
        //   * server concurrency limit of 1
        //   * 5ms to serve a request
        //
        // Serving the bursty client by itself is going to take 50s. That is fine for that client, because it
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

        servers = servers(IntStream.range(0, numServers)
                .mapToObj(i -> SimulationServer.builder()
                        .serverName("node" + i)
                        .simulation(simulation)
                        .handler(h ->
                                h.respond200UntilCapacity(429, concurrencyLimit).responseTime(responseTime))
                        .build())
                .toArray(SimulationServer[]::new));

        Channel concurrencyLimitedChannel = Strategy.concurrencyLimiter(simulation, servers);

        List<EndpointChannelFactory> endpointChannelFactories =
                Collections.singletonList(endpoint -> request -> concurrencyLimitedChannel.execute(endpoint, request));

        Supplier<Channel> stickyChannel = StickyEndpointChannels.builder()
                .channels(endpointChannelFactories)
                .channelName(SimulationUtils.CHANNEL_NAME)
                .taggedMetricRegistry(simulation.taggedMetrics())
                .build();

        Benchmark builder = Benchmark.builder().simulation(simulation);
        EndpointChannel slowAndSteadyChannel =
                builder.endpointChannel("slowAndSteady", DEFAULT_ENDPOINT, stickyChannel.get());
        EndpointChannel oneShotBurstChannel =
                builder.endpointChannel("oneShotBurst", DEFAULT_ENDPOINT, stickyChannel.get());

        Stream<ScheduledRequest> slowAndSteadyChannelRequests = builder.infiniteRequests(
                        timeBetweenSlowAndSteadyRequests, () -> slowAndSteadyChannel)
                .limit(numSlowAndSteady);

        Stream<ScheduledRequest> oneShotBurstChannelRequests = builder.infiniteRequests(
                        timeBetweenBurstRequests, () -> oneShotBurstChannel)
                .limit(numBurst);

        result = builder.requestStream(builder.merge(slowAndSteadyChannelRequests, oneShotBurstChannelRequests))
                .stopWhenNumReceived(totalNumRequests)
                .abortAfter(benchmarkDuration.plus(Duration.ofMinutes(1)))
                .run();
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

    private Supplier<Map<String, SimulationServer>> servers(SimulationServer... values) {
        return Suppliers.memoize(
                () -> Arrays.stream(values).collect(Collectors.toMap(SimulationServer::toString, Function.identity())));
    }

    /** Use the {@link #beginAt} method to simulate live-reloads. */
    private Supplier<Map<String, SimulationServer>> liveReloadingServers(
            Supplier<Optional<SimulationServer>>... serverSuppliers) {
        return () -> Arrays.stream(serverSuppliers)
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
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
        if (result == null) {
            return;
        }

        Stopwatch after = Stopwatch.createStarted();
        Duration serverCpu = Duration.ofNanos(
                MetricNames.globalServerTimeNanos(simulation.taggedMetrics()).getCount());
        long clientMeanNanos = (long) result.clientHistogram().getMean();
        double clientMeanMillis = TimeUnit.NANOSECONDS.toMillis(clientMeanNanos);

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

        String methodName = testInfo.getDisplayName();

        Path txt = Paths.get("src/test/resources/txt/" + methodName + ".txt");
        String pngPath = "src/test/resources/" + methodName + ".png";
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
            Files.write(
                    txt,
                    longSummary.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            XYChart activeRequestsPerServerNode =
                    simulation.metricsReporter().chart(MetricNames.serverActiveRequestsPattern());
            activeRequestsPerServerNode.setTitle(String.format(
                    "%s success=%s%% client_mean=%.1f ms server_cpu=%s",
                    methodName, result.successPercentage(), clientMeanMillis, serverCpu));

            // Github UIs don't let you easily diff pngs that are stored in git lfs. We just keep around the .prev.png
            // on disk to aid local iteration.
            if (Files.exists(Paths.get(pngPath))) {
                Path previousPng = Paths.get(pngPath.replaceAll("\\.png", "\\.prev.png"));
                Files.deleteIfExists(previousPng);
                Files.move(Paths.get(pngPath), previousPng);
            }

            XYChart totalRequestsPerServerPerNode =
                    simulation.metricsReporter().chart(MetricNames.serverRequestMeterPattern());

            XYChart totalRequestsPerClientPerEndpoint =
                    simulation.metricsReporter().chart(MetricNames.perClientEndpointResponseTimerPattern());
            SimulationMetricsReporter.png(
                    pngPath,
                    activeRequestsPerServerNode,
                    totalRequestsPerServerPerNode,
                    totalRequestsPerClientPerEndpoint
                    // simulation.metrics().chart(Pattern.compile("(responseClose|globalResponses)"))
                    );
            log.info("Generated {} ({} ms)", pngPath, sw.elapsed(TimeUnit.MILLISECONDS));
        }

        assertThat(result.responsesLeaked())
                .describedAs("There should be no unclosed responses")
                .isZero();
        log.warn("after() ({} ms)", after.elapsed(TimeUnit.MILLISECONDS));
    }

    @BeforeEach
    public void before() {
        // purely a perf-optimization
        simulation.metricsReporter().onlyRecordMetricsFor(MetricNames::reportedMetricsPredicate);

        Tracer.setSampler(() -> false);
        Tracer.initTrace(Observability.DO_NOT_SAMPLE, Tracers.randomId());
    }

    @AfterAll
    public static void afterClass() throws IOException {
        // squish all txt files together into one markdown report so that github displays diffs
        String txtSection = buildTxtSection();
        String images = buildImagesTable();
        String report = String.format(
                "# Report%n<!-- Run SimulationTest to regenerate this report. -->%n%s%n%n%s%n", txtSection, images);
        Files.write(Paths.get("src/test/resources/report.md"), report.getBytes(StandardCharsets.UTF_8));
    }

    private static String buildTxtSection() throws IOException {
        try (Stream<Path> list = Files.list(Paths.get("src/test/resources/txt"))) {
            List<Path> files = list.filter(p -> !p.toString().endsWith("report.md"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());

            return files.stream()
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
        }
    }

    private static String buildImagesTable() throws IOException {
        try (Stream<Path> files = Files.list(Paths.get("src/test/resources"))) {
            return files.filter(
                            p -> p.toString().endsWith("png") && !p.toString().endsWith(".prev.png"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .map(p -> {
                        String githubLfsUrl = "https://media.githubusercontent.com/media/palantir/dialogue/develop/"
                                + "simulation/src/test/resources/"
                                + p.getFileName();
                        return String.format(
                                "%n## `%s`%n"
                                        + "<table><tr><th>develop</th><th>current</th></tr>%n"
                                        + "<tr>"
                                        + "<td><image width=400 src=\"%s\" /></td>"
                                        + "<td><image width=400 src=\"%s\" /></td>"
                                        + "</tr>"
                                        + "</table>%n%n",
                                p.getFileName().toString().replaceAll("\\.png", ""), githubLfsUrl, p.getFileName());
                    })
                    .collect(Collectors.joining());
        }
    }
}
