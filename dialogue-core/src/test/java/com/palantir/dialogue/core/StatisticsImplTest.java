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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Test;

public class StatisticsImplTest {

    Endpoint endpoint = mock(Endpoint.class);
    Request request = mock(Request.class);
    Ticker ticker = Ticker.systemTicker();

    Statistics.Upstream node1 = ImmutableUpstream.of("node1");
    Statistics.Upstream node2 = ImmutableUpstream.of("node2");

    private static final StatisticsImpl.Randomness DETERMINISTIC = new StatisticsImpl.Randomness() {
        @Override
        public <T> Optional<T> selectRandom(List<T> list) {
            return list.stream().findFirst();
        }
    };

    @Test
    public void no_history_pick_first_node() {
        StatisticsImpl stats = stats(node1, node2);
        Optional<Statistics.Upstream> upstream = stats.getBest(endpoint);
        assertThat(upstream).hasValue(node1);
    }

    @Test
    public void when_one_node_is_happy_pick_that_node() {
        StatisticsImpl stats = stats(node1, node2);

        stats.recordStart(node1, endpoint, request).recordComplete(response(200, "1.56.0"), null);

        Optional<Statistics.Upstream> upstream = stats.getBest(endpoint);
        assertThat(upstream).hasValue(node1);
    }

    @Test
    public void when_one_node_throws_500s_pick_the_other() {
        StatisticsImpl stats = stats(node1, node2);

        stats.recordStart(node1, endpoint, request).recordComplete(response(500, "1.56.0"), null);

        stats.recordStart(node2, endpoint, request).recordComplete(response(200, "1.56.0"), null);

        Optional<Statistics.Upstream> upstream = stats.getBest(endpoint);
        assertThat(upstream).hasValue(node2);
    }

    @Test
    public void when_both_nodes_failing_but_new_version_deployed_pick_the_new_version() {
        StatisticsImpl stats = stats(node1, node2);

        stats.recordStart(node1, endpoint, request).recordComplete(response(500, "1.56.0"), null);
        stats.recordStart(node2, endpoint, request).recordComplete(response(500, "1.56.0"), null);
        stats.recordStart(node2, endpoint, request).recordComplete(response(200, "1.56.1"), null);

        Optional<Statistics.Upstream> upstream = stats.getBest(endpoint);
        assertThat(upstream).hasValue(node2);
    }

    @Test
    public void no_server_header_still_works() {
        StatisticsImpl stats = stats(node1, node2);

        stats.recordStart(node1, endpoint, request).recordComplete(response(500, null), null);
        stats.recordStart(node2, endpoint, request).recordComplete(response(200, null), null);

        Optional<Statistics.Upstream> upstream = stats.getBest(endpoint);
        assertThat(upstream).hasValue(node2);
    }

    @Test
    public void no_server_header_then_header() {
        StatisticsImpl stats = stats(node1, node2);

        stats.recordStart(node1, endpoint, request).recordComplete(response(500, null), null);
        stats.recordStart(node2, endpoint, request).recordComplete(response(200, null), null);
        stats.recordStart(node1, endpoint, request).recordComplete(response(200, "1.56.0"), null);

        Optional<Statistics.Upstream> upstream = stats.getBest(endpoint);
        assertThat(upstream).hasValue(node1);
    }

    @Test
    public void get_best_is_actually_cached() {
        StatisticsImpl stats = stats(node1, node2);

        stats.recordStart(node1, endpoint, request).recordComplete(response(200, "1.56.0"), null);
        stats.recordStart(node2, endpoint, request).recordComplete(response(200, "1.56.0"), null);
        assertThat(stats.computeBest(endpoint)).hasValue(node1);
        assertThat(stats.getBest(endpoint)).hasValue(node1);

        stats.recordStart(node1, endpoint, request).recordComplete(response(500, "1.56.0"), null);
        assertThat(stats.computeBest(endpoint)).hasValue(node2);
        assertThat(stats.getBest(endpoint)).hasValue(node2);
    }

    @Test
    public void deterministic_time() {
        ticker = mock(Ticker.class);
        StatisticsImpl stats = stats(node1, node2);

        for (int i = 0; i < 200; i++) {
            stats.recordStart(node1, endpoint, request).recordComplete(response(200, "1.56.0"), null);
        }

        when(ticker.read()).thenReturn(Duration.ofMinutes(1).toNanos());
        stats.recordStart(node1, endpoint, request).recordComplete(response(500, "1.56.0"), null);

        assertThat(stats.computeBest(endpoint)).hasValue(node1);
    }

    @Test
    public void deterministic_time2() {
        try (SimulatedScheduler scheduler = new SimulatedScheduler()) {
            StatisticsImpl stats =
                    new StatisticsImpl(() -> ImmutableList.of(node1, node2), DETERMINISTIC, scheduler.ticker());

            // fire off a request every second
            int numSeconds = 50;
            for (int i = 0; i < numSeconds; i++) {
                ListenableScheduledFuture<Statistics.InFlightStage> startedFuture = scheduler.schedule(
                        () -> {
                            return stats.recordStart(node1, endpoint, request);
                        },
                        i,
                        TimeUnit.SECONDS);

                Futures.transformAsync(
                        startedFuture,
                        stage -> scheduler.schedule(
                                () -> {
                                    stage.recordComplete(response(200, "1.56.0"), null);
                                },
                                1,
                                TimeUnit.MILLISECONDS),
                        MoreExecutors.directExecutor());
            }

            scheduler.schedule(
                    () -> {
                        System.out.println("The time is "
                                + Duration.ofNanos(scheduler.ticker().read()));
                        System.out.println(stats.getBest(endpoint));
                    },
                    numSeconds,
                    TimeUnit.SECONDS);
        }
    }

    private Response response(int status, String version) {
        return new Response() {
            @Override
            public InputStream body() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public int code() {
                return status;
            }

            @Override
            public Map<String, List<String>> headers() {
                if (version == null) {
                    return ImmutableMap.of();
                }
                return ImmutableMap.of("server", ImmutableList.of("foundry-catalog/" + version));
            }
        };
    }

    private StatisticsImpl stats(Statistics.Upstream... upstreams) {
        return new StatisticsImpl(() -> ImmutableList.copyOf(upstreams), DETERMINISTIC, ticker);
    }

    /** Combined ticker and scheduler. */
    private static class SimulatedScheduler implements Closeable {

        private final DeterministicScheduler deterministicExecutor = new DeterministicScheduler();
        private final ListeningScheduledExecutorService listenableExecutor =
                MoreExecutors.listeningDecorator(deterministicExecutor);
        private final TestTicker ticker = new TestTicker();

        public <T> ListenableScheduledFuture<T> schedule(Callable<T> command, long delay, TimeUnit unit) {
            long scheduleTime = ticker.read();
            long delayNanos = unit.toNanos(delay);

            return listenableExecutor.schedule(
                    new Callable<T>() {
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

        public Ticker ticker() {
            return ticker; // read only!
        }

        public void advanceTo(Duration duration) {
            deterministicExecutor.tick(duration.toNanos(), TimeUnit.NANOSECONDS);
            ticker.advanceTo(duration);
        }

        @Override
        public void close() {
            advanceTo(Duration.ofNanos(Long.MAX_VALUE));
        }
    }

    private static class TestTicker implements Ticker {
        private long nanos = 0;

        @Override
        public long read() {
            return nanos;
        }

        public void advanceTo(Duration duration) {
            long newNanos = duration.toNanos();
            Preconditions.checkArgument(
                    newNanos >= nanos,
                    "TestTicker time may not go backwards. Current: " + nanos + " update: " + newNanos);
            nanos = newNanos;
        }
    }
}
