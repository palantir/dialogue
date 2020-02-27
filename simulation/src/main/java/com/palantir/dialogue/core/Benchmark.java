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

import com.codahale.metrics.Snapshot;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exercises the given {@link Channel} using a pre-defined number of requests, all scheduled on the
 * {@link Simulation} executor.
 */
public final class Benchmark {
    private static final Logger log = LoggerFactory.getLogger(Benchmark.class);
    static final String REQUEST_ID_HEADER = "simulation-req-id";

    private Simulation simulation;
    private Duration delayBetweenRequests;
    private Channel[] clients;
    private IntSupplier clientIndexChooser;
    private Stream<ScheduledRequest> requestStream;
    private Function<Long, Request> requestSupplier = Benchmark::constructRequest;
    private ShouldStopPredicate benchmarkFinished;
    private Duration abortAfter;

    private Benchmark() {}

    static Benchmark builder() {
        return new Benchmark();
    }

    public Benchmark requestsPerSecond(int rps) {
        delayBetweenRequests = Duration.ofSeconds(1).dividedBy(rps);
        return this;
    }

    public Benchmark numRequests(long numRequests) {
        Preconditions.checkState(requestStream == null, "Already set up requests");
        requestStream = infiniteRequests(delayBetweenRequests).limit(numRequests);
        stopWhenNumReceived(numRequests);
        return this;
    }

    public Benchmark sendUntil(Duration cutoff) {
        Preconditions.checkState(requestStream == null, "Already set up requests");
        long num = cutoff.toNanos() / delayBetweenRequests.toNanos();
        return numRequests(num);
    }

    public Benchmark randomEndpoints(Endpoint... endpoints) {
        return endpoints(true, endpoints);
    }

    public Benchmark endpoints(boolean randomize, Endpoint... endpoints) {
        Preconditions.checkNotNull(requestStream, "Must call sendUntil or numRequests first");
        Random pseudoRandom = new Random(21876781263L);
        requestStream = requestStream.map(req -> {
            int index = randomize ? pseudoRandom.nextInt(endpoints.length) : (int) (req.number() % endpoints.length);
            return ImmutableScheduledRequest.builder()
                    .from(req)
                    .endpoint(endpoints[index])
                    .build();
        });
        return this;
    }

    public Benchmark simulation(Simulation sim) {
        simulation = sim;
        return this;
    }

    public Benchmark client(Channel value) {
        clients(1, unused -> value);
        this.clientIndexChooser = () -> 0;
        return this;
    }

    /** Use this if you want to simulate a bunch of clients. */
    public Benchmark clients(int numClients, IntFunction<Channel> clientFunction) {
        this.clients = IntStream.range(0, numClients).mapToObj(clientFunction).toArray(Channel[]::new);
        Random pseudoRandom = new Random(1231234L);
        this.clientIndexChooser = () -> pseudoRandom.nextInt(numClients);
        return this;
    }

    private Benchmark stopWhenNumReceived(long numReceived) {
        SettableFuture<Void> future = SettableFuture.create();
        benchmarkFinished = new ShouldStopPredicate() {
            @Override
            public SettableFuture<Void> getFuture() {
                return future;
            }

            @Override
            public void update(Duration _time, long _requestsStarted, long responsesReceived) {
                if (responsesReceived >= numReceived) {
                    future.set(null);
                }
            }
        };
        return this;
    }

    @SuppressWarnings({"FutureReturnValueIgnored", "CheckReturnValue"})
    public Benchmark abortAfter(Duration value) {
        this.abortAfter = value;
        simulation
                .scheduler()
                .schedule(
                        () -> {
                            log.warn(
                                    "Aborted running benchmark after cutoff reached - strategy might be buggy {}",
                                    value);
                            benchmarkFinished.getFuture().set(null);
                        },
                        value.toNanos(),
                        TimeUnit.NANOSECONDS);
        return this;
    }

    public BenchmarkResult run() {
        ListenableFuture<BenchmarkResult> result = schedule();
        Stopwatch run = Stopwatch.createStarted();
        simulation.runClockToInfinity(Optional.ofNullable(abortAfter));
        log.info("Ran clock to infinity ({} ms)", run.elapsed(TimeUnit.MILLISECONDS));
        return Futures.getUnchecked(result);
    }

    @SuppressWarnings({"FutureReturnValueIgnored", "CheckReturnValue"})
    public ListenableFuture<BenchmarkResult> schedule() {
        DialogueClientMetrics clientMetrics = DialogueClientMetrics.of(simulation.taggedMetrics());

        Channel[] channels = Arrays.stream(clients)
                .map(c -> new InstrumentedChannel(c, clientMetrics))
                .toArray(Channel[]::new);

        long[] requestsStarted = {0};
        long[] responsesReceived = {0};
        Map<String, Integer> statusCodes = new TreeMap<>();

        Stopwatch scheduling = Stopwatch.createStarted();
        requestStream.forEach(req -> {
            FutureCallback<Response> accumulateStatusCodes = new FutureCallback<Response>() {
                @Override
                public void onSuccess(Response response) {
                    response.close(); // just being a good citizen
                    statusCodes.compute(Integer.toString(response.code()), (c, num) -> num == null ? 1 : num + 1);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.warn("Benchmark onFailure requestNum={}", req.number(), throwable);
                    statusCodes.compute(throwable.getMessage(), (c, num) -> num == null ? 1 : num + 1);
                }
            };

            simulation
                    .scheduler()
                    .schedule(
                            () -> {
                                log.info(
                                        "time={} starting num={} {}",
                                        simulation.clock().read(),
                                        req.number(),
                                        req);
                                Channel channel = channels[clientIndexChooser.getAsInt()];
                                try {
                                    ListenableFuture<Response> future = channel.execute(req.endpoint(), req.request());
                                    requestsStarted[0] += 1;

                                    Futures.addCallback(future, accumulateStatusCodes, MoreExecutors.directExecutor());
                                    future.addListener(
                                            () -> {
                                                responsesReceived[0] += 1;
                                                benchmarkFinished.update(
                                                        Duration.ofNanos(simulation
                                                                .clock()
                                                                .read()),
                                                        requestsStarted[0],
                                                        responsesReceived[0]);
                                            },
                                            MoreExecutors.directExecutor());
                                } catch (RuntimeException e) {
                                    log.error("Channels shouldn't throw", e);
                                }
                            },
                            req.sendTime().toNanos(),
                            TimeUnit.NANOSECONDS);
        });
        log.info("Scheduled all requests ({} ms)", scheduling.elapsed(TimeUnit.MILLISECONDS));

        benchmarkFinished.getFuture().addListener(simulation.metricsReporter()::report, MoreExecutors.directExecutor());

        return Futures.transform(
                benchmarkFinished.getFuture(),
                v -> {
                    long numGlobalResponses = MetricNames.globalResponses(simulation.taggedMetrics())
                            .getCount();
                    long leaked = numGlobalResponses
                            - MetricNames.responseClose(simulation.taggedMetrics())
                                    .getCount();
                    return ImmutableBenchmarkResult.builder()
                            .clientHistogram(clientMetrics
                                    .response(SimulationUtils.SERVICE_NAME)
                                    .getSnapshot())
                            .endTime(Duration.ofNanos(simulation.clock().read()))
                            .statusCodes(statusCodes)
                            .successPercentage(
                                    Math.round(statusCodes.getOrDefault("200", 0) * 1000d / requestsStarted[0]) / 10d)
                            .numSent(requestsStarted[0])
                            .numReceived(responsesReceived[0])
                            .numGlobalResponses(numGlobalResponses)
                            .responsesLeaked(leaked)
                            .build();
                },
                MoreExecutors.directExecutor());
    }

    private Stream<ScheduledRequest> infiniteRequests(Duration interval) {
        return LongStream.iterate(0, current -> current + 1).mapToObj(number -> {
            return ImmutableScheduledRequest.builder()
                    .number(number)
                    .request(requestSupplier.apply(number))
                    .sendTime(interval.multipliedBy(number))
                    .build();
        });
    }

    @Value.Immutable
    interface BenchmarkResult {
        Snapshot clientHistogram();

        Duration endTime();

        Map<String, Integer> statusCodes();

        /** What proportion of responses were 200s. */
        double successPercentage();

        /** How many requests did we fire off in the benchmark. */
        long numSent();

        /** How many responses were returned to the user. */
        long numReceived();

        /** How many responses were issued in total across all servers. */
        long numGlobalResponses();

        /** How many responses were never closed. */
        long responsesLeaked();
    }

    /**
     * Determines when the benchmark terminates - useful when a server is behaving like a black hole (not returning).
     */
    interface ShouldStopPredicate {
        /** Called once to set up a future - when this resolves, the benchmark will stop. */
        SettableFuture<Void> getFuture();

        /**
         * Called after every request to give this predicate the opportunity to terminate the benchmark by
         * resolving the SettableFuture.
         */
        void update(Duration time, long requestsStarted, long responsesReceived);
    }

    @Value.Immutable
    interface ScheduledRequest {
        Endpoint ENDPOINT = SimulationUtils.endpoint("endpoint");

        long number();

        Duration sendTime();

        Request request();

        @Value.Default
        default Endpoint endpoint() {
            return ENDPOINT;
        }
    }

    private static Request constructRequest(long number) {
        return Request.builder()
                .putHeaderParams(REQUEST_ID_HEADER, Long.toString(number))
                .build();
    }
}
