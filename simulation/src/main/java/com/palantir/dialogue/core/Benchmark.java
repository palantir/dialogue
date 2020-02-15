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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constant rate. */
public final class Benchmark {
    private static final Logger log = LoggerFactory.getLogger(Benchmark.class);

    private int requestsPerSecond;
    private int numRequests;
    private Simulation simulation;
    private Function<Integer, ListenableFuture<Response>> channel;

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

    public ListenableFuture<Void> run() {
        Instant realStart = Instant.now();
        SettableFuture<Void> done = SettableFuture.create();

        Runnable stopReporting = simulation.metrics().startReporting(Duration.ofSeconds(1));
        done.addListener(stopReporting, MoreExecutors.directExecutor());

        int[] outstanding = new int[] {numRequests}; // don't need atomicinteger as we only have one thread
        Runnable maybeMarkFinished = () -> {
            outstanding[0] -= 1;
            if (outstanding[0] == 0) {
                done.set(null);
            }
        };

        HistogramChannel histogramChannel = new HistogramChannel(simulation, channel);
        Duration intervalBetweenRequests = Duration.ofSeconds(1).dividedBy(requestsPerSecond);
        IntStream.range(0, numRequests).forEach(requestNum -> {
            simulation.schedule(
                    () -> {
                        ListenableFuture<Response> serverFuture = histogramChannel.apply(requestNum);
                        serverFuture.addListener(maybeMarkFinished, MoreExecutors.directExecutor());
                    },
                    requestNum * intervalBetweenRequests.toNanos(),
                    TimeUnit.NANOSECONDS);
        });

        done.addListener(
                () -> {
                    Snapshot snapshot = histogramChannel.getHistogram().getSnapshot();
                    log.info(
                            "Finished simulation: num={} time={}, mean={}, real time={}",
                            numRequests,
                            Duration.ofNanos(simulation.clock().read()),
                            Duration.ofNanos((long) snapshot.getMean()),
                            Duration.between(realStart, Instant.now()));
                },
                MoreExecutors.directExecutor());

        return done;
    }
}
