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
import static org.mockito.Mockito.when;

import com.codahale.metrics.Snapshot;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constant rate. */
public final class Benchmark {
    private static final Logger log = LoggerFactory.getLogger(Benchmark.class);
    private static final Endpoint ENDPOINT = mock(Endpoint.class);

    private Simulation simulation;
    private Channel channel;
    private Duration requestInterval;
    private Stream<ScheduledRequest> requestStream;
    private Function<Integer, Request> requestSupplier = Benchmark::constructRequest;
    private ShouldStopPredicate benchmarkFinished;

    private Benchmark() {}

    static Benchmark builder() {
        return new Benchmark();
    }

    public Benchmark requestsPerSecond(int rps) {
        requestInterval = Duration.ofSeconds(1).dividedBy(rps);
        return this;
    }

    public Benchmark numRequests(int numRequests) {
        requestStream = infiniteRequests(requestInterval).limit(numRequests);
        stopWhenNumReceived(numRequests);
        return this;
    }

    public Benchmark sendUntil(Duration cutoff) {
        long num = cutoff.dividedBy(requestInterval);
        requestStream = infiniteRequests(requestInterval).limit(num);
        stopWhenNumReceived(num);
        return this;
    }

    public Benchmark simulation(Simulation sim) {
        simulation = sim;
        return this;
    }

    public Benchmark channel(Channel value) {
        channel = value;
        return this;
    }

    public Benchmark stopWhenNumReceived(long numReceived) {
        benchmarkFinished = (time, _requestsStarted, responsesReceived) ->
                (time.compareTo(Duration.ofDays(1)) > 0) || responsesReceived >= numReceived;
        return this;
    }

    public BenchmarkResult run() {
        SettableFuture<BenchmarkResult> result = schedule();
        simulation.runClockToInfinity();
        return Futures.getUnchecked(result);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public SettableFuture<BenchmarkResult> schedule() {
        Instant realStart = Instant.now();
        SettableFuture<BenchmarkResult> done = SettableFuture.create();

        HistogramChannel histogramChannel = new HistogramChannel(simulation, channel);

        int[] requestsStarted = new int[] {0};
        int[] responsesReceived = new int[] {0};

        Map<String, Integer> statusCodes = new HashMap<>();
        Runnable maybeTerminate = () -> {
            responsesReceived[0] += 1;

            if (benchmarkFinished.shouldStop(
                    Duration.ofNanos(simulation.clock().read()), requestsStarted[0], responsesReceived[0])) {
                BenchmarkResult result = ImmutableBenchmarkResult.builder()
                        .clientHistogram(histogramChannel.getHistogram().getSnapshot())
                        .endTime(Duration.ofNanos(simulation.clock().read()))
                        .statusCodes(statusCodes)
                        .successPercentage(Math.round(statusCodes.get("200") * 1000d / responsesReceived[0]) / 10d)
                        .build();
                log.info(
                        "Finished simulation: client_mean={}, end_time={}, success={}% codes={} ({} ms)",
                        Duration.ofNanos((long) result.clientHistogram().getMean()),
                        result.endTime(),
                        result.successPercentage(),
                        result.statusCodes(),
                        Duration.between(realStart, Instant.now()));
                done.set(result);
            }
        };

        requestStream.forEach(req -> {
            FutureCallback<Response> accumulateStatusCodes = new FutureCallback<Response>() {
                @Override
                public void onSuccess(Response response) {
                    statusCodes.compute(Integer.toString(response.code()), (c, num) -> num == null ? 1 : num + 1);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.warn("Benchmark onFailure requestNum={}", req.number(), throwable);
                    statusCodes.compute(throwable.getClass().toString(), (c, num) -> num == null ? 1 : num + 1);
                }
            };

            simulation.schedule(
                    () -> {
                        log.debug(
                                "time={} kicking off request {}",
                                simulation.clock().read(),
                                req.number());
                        ListenableFuture<Response> future = histogramChannel.execute(ENDPOINT, req.request());
                        requestsStarted[0] += 1;

                        Futures.addCallback(future, accumulateStatusCodes, MoreExecutors.directExecutor());
                        future.addListener(maybeTerminate, MoreExecutors.directExecutor());
                    },
                    req.sendTime().toNanos(),
                    TimeUnit.NANOSECONDS);
        });

        return done;
    }

    private Stream<ScheduledRequest> infiniteRequests(Duration interval) {
        return Stream.iterate(0, current -> current + 1).map(number -> {
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

        double successPercentage();
    }

    interface ShouldStopPredicate {
        boolean shouldStop(Duration time, int requestsStarted, int responsesReceived);
    }

    @Value.Immutable
    interface ScheduledRequest {
        int number();

        Duration sendTime();

        Request request();
    }

    static Request constructRequest(int number) {
        Request req = mock(Request.class);
        when(req.headerParams()).thenReturn(ImmutableMap.of("X-B3-TraceId", "req-" + number));
        return req;
    }
}
