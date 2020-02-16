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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constant rate. */
public final class Benchmark {
    private static final Logger log = LoggerFactory.getLogger(Benchmark.class);

    private int requestsPerSecond;
    private int numRequests;
    private Simulation simulation;
    private Function<Integer, ListenableFuture<Response>> channel;
    private ShouldStopPredicate shouldStop;

    private Benchmark() {}

    static Benchmark builder() {
        return new Benchmark().requestsPerSecond(1000).numRequests(20).stopWhenAllRequestsReturn();
    }

    public Benchmark requestsPerSecond(int rps) {
        requestsPerSecond = rps;
        return this;
    }

    public Benchmark numRequests(int value) {
        numRequests = value;
        return this;
    }

    public Benchmark simulation(Simulation sim) {
        simulation = sim;
        return this;
    }

    public Benchmark channel(Function<Integer, ListenableFuture<Response>> channelExecute) {
        channel = channelExecute;
        return this;
    }

    public Benchmark stopWhenAllRequestsReturn() {
        shouldStop = (time, _requestsStarted, responsesReceived) ->
                (time.compareTo(Duration.ofDays(1)) > 0) || responsesReceived >= numRequests;
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
        Duration intervalBetweenRequests = Duration.ofSeconds(1).dividedBy(requestsPerSecond);

        int[] requestsStarted = new int[] {0};
        int[] responsesReceived = new int[] {0};

        Map<String, Integer> statusCodes = new HashMap<>();
        Runnable maybeTerminate = () -> {
            responsesReceived[0] += 1;

            if (shouldStop.shouldStop(
                    Duration.ofNanos(simulation.clock().read()), requestsStarted[0], responsesReceived[0])) {
                BenchmarkResult result = ImmutableBenchmarkResult.builder()
                        .clientHistogram(histogramChannel.getHistogram().getSnapshot())
                        .endTime(Duration.ofNanos(simulation.clock().read()))
                        .statusCodes(statusCodes)
                        .successPercentage(Math.round(statusCodes.get("200") * 1000d / numRequests) / 10d)
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

        IntStream.range(0, numRequests).forEach(requestNum -> {
            if (requestNum % (numRequests / 300) == 0) {
                log.debug("Scheduling request {}", requestNum); // above 10k total requests, this gets really slow!
            }

            FutureCallback<Response> accumulateStatusCodes = new FutureCallback<Response>() {
                @Override
                public void onSuccess(Response response) {
                    statusCodes.compute(Integer.toString(response.code()), (c, num) -> num == null ? 1 : num + 1);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.warn("Benchmark onFailure requestNum={}", requestNum, throwable);
                    statusCodes.compute(throwable.getClass().toString(), (c, num) -> num == null ? 1 : num + 1);
                }
            };

            simulation.schedule(
                    () -> {
                        log.debug(
                                "time={} kicking off request {}",
                                simulation.clock().read(),
                                requestNum);
                        ListenableFuture<Response> future = histogramChannel.apply(requestNum);
                        requestsStarted[0] += 1;

                        Futures.addCallback(future, accumulateStatusCodes, MoreExecutors.directExecutor());
                        future.addListener(maybeTerminate, MoreExecutors.directExecutor());
                    },
                    requestNum * intervalBetweenRequests.toNanos(),
                    TimeUnit.NANOSECONDS);
        });

        return done;
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
}
