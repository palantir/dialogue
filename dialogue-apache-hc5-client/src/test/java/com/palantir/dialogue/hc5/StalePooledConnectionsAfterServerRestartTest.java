/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.core.DialogueChannel;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.HttpString;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Pooled connections after a server restart, and {@link ApacheHttpClientChannels.CloseableClient#closeIdleConnections}.
 * <p>
 * When a server advertises {@code Keep-Alive: timeout=N} (Witchcraft sends {@code timeout=180}),
 * {@link InactivityValidationAwareConnectionKeepAliveStrategy} raises the pool's validate-after-inactivity
 * threshold to N, so pooled connections idle for less than N are reused without a stale check. When the server
 * restarts, every pooled connection to it is dead, and each one fails the next request that uses it. A caller that
 * learns of this, e.g. from a request that failed at once, can call {@code closeIdleConnections()} so that later
 * requests open new connections.
 * <p>
 * Each restart test grows the pool to {@link #POOLED_CONNECTIONS} connections, restarts the server on the same port,
 * waits past the default 4 s inactivity threshold but well inside the advertised keep-alive timeout, then sends
 * requests one at a time from a client without retries.
 */
public final class StalePooledConnectionsAfterServerRestartTest {

    private static final int POOLED_CONNECTIONS = 20;
    private static final int REQUESTS_AFTER_RESTART = POOLED_CONNECTIONS + 10;
    // Longer than the default validate-after-inactivity threshold (4 s), shorter than the advertised keep-alive.
    private static final Duration IDLE_BEFORE_REUSE = Duration.ofSeconds(5);
    private static final Request REQUEST = Request.builder().build();

    @Test
    void afterARestartEachDeadPooledConnectionFailsARequest() throws Exception {
        Result result = run(true, false);
        assertThat(result.failures)
                .as("requests failing after the restart (%s)", result)
                .isEqualTo(POOLED_CONNECTIONS);
    }

    @Test
    void closingIdleConnectionsAfterARestartPreventsTheFailures() throws Exception {
        Result result = run(true, true);
        assertThat(result.failures)
                .as("requests failing after the restart and closeIdleConnections() (%s)", result)
                .isZero();
    }

    /** Control: without the Keep-Alive header, the default 4 s validation catches the dead connections. */
    @Test
    void withoutKeepAliveHeaderDeadConnectionsAreValidatedAway() throws Exception {
        Result result = run(false, false);
        assertThat(result.failures).as("no Keep-Alive header (%s)", result).isZero();
    }

    @Test
    void closeIdleConnectionsDoesNotAffectARequestInFlight() throws Exception {
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Undertow server = Undertow.builder()
                .setHandler(new BlockingHandler(exchange -> {
                    requestArrived.countDown();
                    release.await(30, TimeUnit.SECONDS);
                    exchange.setStatusCode(200);
                }))
                .addHttpsListener(0, "localhost", SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG))
                .build();
        server.start();
        String uri = "https://localhost:" + getPort(server);
        ClientConfiguration conf = clientConfig(uri);
        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, "stale-pool-test")) {
            ListenableFuture<Response> inFlight = channel(conf, client).execute(TestEndpoint.POST, REQUEST);
            assertThat(requestArrived.await(30, TimeUnit.SECONDS)).isTrue();

            client.closeIdleConnections();
            release.countDown();

            try (Response response = inFlight.get(30, TimeUnit.SECONDS)) {
                assertThat(response.code()).isEqualTo(200);
            }
        } finally {
            server.stop();
        }
    }

    private static Result run(boolean advertiseKeepAlive, boolean closeIdleConnectionsAfterRestart) throws Exception {
        CountDownLatch allArrived = new CountDownLatch(POOLED_CONNECTIONS);
        Undertow first = startServer(0, advertiseKeepAlive, allArrived);
        int port = getPort(first);
        String uri = "https://localhost:" + port;
        ClientConfiguration conf = clientConfig(uri);
        Undertow second = null;
        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, "stale-pool-test")) {
            // Raw channel: no concurrency limiter, so the pool really grows to POOLED_CONNECTIONS connections.
            Channel raw = ApacheHttpClientChannels.createSingleUri(uri, client);
            List<ListenableFuture<Response>> warmup = new ArrayList<>();
            for (int i = 0; i < POOLED_CONNECTIONS; i++) {
                warmup.add(raw.execute(TestEndpoint.POST, REQUEST));
            }
            for (ListenableFuture<Response> future : warmup) {
                try (Response response = future.get(30, TimeUnit.SECONDS)) {
                    assertThat(response.code()).isEqualTo(200);
                }
            }

            // "Restart": every connection pooled so far belonged to the first server process.
            first.stop();
            second = startServer(port, advertiseKeepAlive, new CountDownLatch(0));
            Uninterruptibles.sleepUninterruptibly(IDLE_BEFORE_REUSE);
            if (closeIdleConnectionsAfterRestart) {
                client.closeIdleConnections();
            }

            long start = System.nanoTime();
            int failures = countFailures(channel(conf, client), REQUESTS_AFTER_RESTART);
            return new Result(failures, Duration.ofNanos(System.nanoTime() - start));
        } finally {
            first.stop();
            if (second != null) {
                second.stop();
            }
        }
    }

    private static int countFailures(Channel channel, int requests) throws Exception {
        int failures = 0;
        for (int i = 0; i < requests; i++) {
            try (Response response = channel.execute(TestEndpoint.POST, REQUEST).get(30, TimeUnit.SECONDS)) {
                assertThat(response.code()).isEqualTo(200);
            } catch (ExecutionException e) {
                failures++;
            }
        }
        return failures;
    }

    private static ClientConfiguration clientConfig(String uri) {
        return ClientConfiguration.builder()
                .from(TestConfigurations.create(uri))
                .maxNumRetries(0)
                .build();
    }

    private static Channel channel(ClientConfiguration conf, ApacheHttpClientChannels.CloseableClient client) {
        return DialogueChannel.builder()
                .channelName("stale-pool-test")
                .clientConfiguration(conf)
                .factory(args -> ApacheHttpClientChannels.createSingleUri(args, client))
                .build();
    }

    private record Result(int failures, Duration elapsed) {
        @Override
        public String toString() {
            return failures + " of " + REQUESTS_AFTER_RESTART + " requests failed in " + elapsed.toMillis() + " ms";
        }
    }

    private static Undertow startServer(int port, boolean advertiseKeepAlive, CountDownLatch holdUntilAllArrived) {
        HttpHandler handler = new BlockingHandler(exchange -> {
            // Hold the first requests until all have arrived, so each one gets its own connection.
            holdUntilAllArrived.countDown();
            holdUntilAllArrived.await(30, TimeUnit.SECONDS);
            if (advertiseKeepAlive) {
                exchange.getResponseHeaders().put(HttpString.tryFromString("Keep-Alive"), "timeout=180");
            }
            exchange.setStatusCode(200);
        });
        Undertow server = Undertow.builder()
                .setHandler(handler)
                .addHttpsListener(port, "localhost", SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG))
                .build();
        server.start();
        return server;
    }

    private static int getPort(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }
}
