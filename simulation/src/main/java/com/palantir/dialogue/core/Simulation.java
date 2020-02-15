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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Combined ScheduledExecutorService and Clock. */
final class Simulation {

    private static final Logger log = LoggerFactory.getLogger(Simulation.class);

    private final DeterministicScheduler deterministicExecutor = new DeterministicScheduler();
    private final ListeningScheduledExecutorService listenableExecutor =
            MoreExecutors.listeningDecorator(deterministicExecutor);
    private final TestCaffeineTicker ticker = new TestCaffeineTicker();
    private final SimulationMetrics metrics = new SimulationMetrics(this);
    private final CodahaleClock codahaleClock = new CodahaleClock(ticker);

    public Simulation() {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> log.error("Uncaught throwable", e));
    }

    public <T> ListenableScheduledFuture<T> schedule(Callable<T> command, long delay, TimeUnit unit) {
        long scheduleTime = ticker.read();
        long delayNanos = unit.toNanos(delay);

        return listenableExecutor.schedule(
                () -> {
                    ticker.advanceTo(Duration.ofNanos(scheduleTime + delayNanos));
                    return command.call();
                },
                delay,
                unit);
    }

    public Ticker clock() {
        return ticker; // read only!
    }

    public CodahaleClock codahaleClock() {
        return codahaleClock;
    }

    public SimulationMetrics metrics() {
        return metrics;
    }

    public void runClockToInfinity() {
        advanceTo(Duration.ofNanos(Long.MAX_VALUE));
    }

    private void advanceTo(Duration duration) {
        deterministicExecutor.tick(duration.toNanos(), TimeUnit.NANOSECONDS);
        ticker.advanceTo(duration);
    }

    private static final class TestCaffeineTicker implements Ticker {
        private long nanos = 0;

        @Override
        public long read() {
            return nanos;
        }

        public void advanceTo(Duration duration) {
            long newNanos = duration.toNanos();
            Preconditions.checkArgument(
                    newNanos >= nanos, "TestTicker time may not go backwards current=%s new=%s", nanos, newNanos);
            nanos = newNanos;
        }
    }

    public BasicSimulationServer.Builder newServer() {
        return SimulationServer.builder().simulation(this);
    }
}
