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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constant rate. */
public final class Benchmark {
    private static final Logger log = LoggerFactory.getLogger(Benchmark.class);

    private int requestsPerSecond = 1000;
    private int numRequests = 20;
    private Simulation simulation;
    private Function<Integer, ListenableFuture<Response>> channel;
    private final List<Runnable> onCompletion = new ArrayList<>();

    private final Random random = new Random(12345L);

    private Benchmark() {}

    static Benchmark builder() {
        return new Benchmark();
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

    public Benchmark onCompletion(Runnable runnable) {
        onCompletion.add(runnable);
        return this;
    }

    public void run() {
        schedule();
        simulation.runClockToInfinity();
    }

    public ListenableFuture<Void> schedule() {
        Instant realStart = Instant.now();
        SettableFuture<Void> done = SettableFuture.create();

        int numMetricSamples = 200;
        int checkPoint = numRequests / numMetricSamples;

        HistogramChannel histogramChannel = new HistogramChannel(simulation, channel);
        Duration intervalBetweenRequests = Duration.ofSeconds(1).dividedBy(requestsPerSecond);

        int[] outstanding = new int[] {numRequests};
        Map<String, Integer> statusCodes = new HashMap<>();

        IntStream.range(0, numRequests).forEach(requestNum -> {
            if (requestNum % checkPoint == 0) {
                log.debug("Scheduling request {}", requestNum);
            }
            simulation.schedule(
                    () -> {
                        log.debug(
                                "time={} kicking off request {}",
                                simulation.clock().read(),
                                requestNum);
                        ListenableFuture<Response> future = histogramChannel.apply(requestNum);
                        Futures.addCallback(
                                future,
                                new FutureCallback<Response>() {
                                    @Override
                                    public void onSuccess(Response response) {
                                        statusCodes.compute(
                                                Integer.toString(response.code()),
                                                (c, num) -> num == null ? 1 : num + 1);
                                    }

                                    @Override
                                    public void onFailure(Throwable throwable) {
                                        log.warn("requestNum={}", requestNum, throwable);
                                        statusCodes.compute(
                                                throwable.getClass().toString(), (c, num) -> num == null ? 1 : num + 1);
                                    }
                                },
                                MoreExecutors.directExecutor());
                        future.addListener(
                                () -> {
                                    outstanding[0] -= 1;
                                    if (outstanding[0] == 0) {
                                        done.set(null);
                                    }

                                    // we sample metrics with a little jitter to avoid misleading harmonic graphs
                                    if ((requestNum + random.nextInt(checkPoint)) % checkPoint == 0) {
                                        log.debug("Reporting metrics at requestNum={}", requestNum);
                                        simulation.metrics().report();
                                    }
                                },
                                MoreExecutors.directExecutor());
                    },
                    requestNum * intervalBetweenRequests.toNanos(),
                    TimeUnit.NANOSECONDS);
        });

        onCompletion(() -> {
            Snapshot snapshot = histogramChannel.getHistogram().getSnapshot();

            log.info(
                    "Finished simulation: client_mean={}, end_time={}, success={}% codes={} ({} ms)", // return typed
                    // stats?
                    Duration.ofNanos((long) snapshot.getMean()),
                    Duration.ofNanos(simulation.clock().read()),
                    Math.round(statusCodes.get("200") * 1000d / numRequests) / 10d,
                    statusCodes,
                    Duration.between(realStart, Instant.now()).toMillis());
        });

        onCompletion.forEach(runnable -> done.addListener(runnable, MoreExecutors.directExecutor()));

        return done;
    }
}
