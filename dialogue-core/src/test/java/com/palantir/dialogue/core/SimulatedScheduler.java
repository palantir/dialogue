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
import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.jmock.lib.concurrent.DeterministicScheduler;

/** Combined ScheduledExecutorService and Clock. */
final class SimulatedScheduler implements Closeable {

    private final DeterministicScheduler deterministicExecutor = new DeterministicScheduler();
    private final ListeningScheduledExecutorService listenableExecutor =
            MoreExecutors.listeningDecorator(deterministicExecutor);
    private final TestTicker ticker = new TestTicker();
    private final SimulationMetrics metrics = new SimulationMetrics(this);

    public <T> ListenableScheduledFuture<T> schedule(Callable<T> command, long delay, TimeUnit unit) {
        long scheduleTime = ticker.read();
        long delayNanos = unit.toNanos(delay);

        return listenableExecutor.schedule(
                new Callable<T>() {
                    @Override
                    public T call() throws Exception {
                        try {
                            ticker.advanceTo(Duration.ofNanos(scheduleTime + delayNanos));
                            return command.call();
                        } catch (Exception e) {
                            System.out.println(e);
                            throw e;
                        }
                    }
                },
                delay,
                unit);
    }

    public ListenableScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return schedule(
                () -> {
                    command.run();
                    return null;
                },
                delay,
                unit);
    }

    public Ticker clock() {
        return ticker; // read only!
    }

    public CodahaleClock codahaleClock() {
        return new CodahaleClock(ticker);
    }

    public SimulationMetrics metrics() {
        return metrics;
    }

    public void advanceTo(Duration duration) {
        deterministicExecutor.tick(duration.toNanos(), TimeUnit.NANOSECONDS);
        ticker.advanceTo(duration);
    }

    @Override
    public void close() {
        advanceTo(Duration.ofNanos(Long.MAX_VALUE));
    }

    private static final class TestTicker implements Ticker {
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
}
