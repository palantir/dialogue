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
package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Meter;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("checkstyle:NestedTryDepth")
public final class LargeResponseTest {

    @ParameterizedTest
    @MethodSource("responseTestArguments")
    public void large_response(CloseType closeType, TransferEncoding encoding) throws Exception {
        AtomicBoolean used = new AtomicBoolean();
        Undertow server = startServer(new BlockingHandler(exchange -> {
            long responseBytes = 300 * 1024 * 1024;
            encoding.apply(exchange, responseBytes);
            String sessionId = Base64.getEncoder()
                    .encodeToString(exchange.getConnection().getSslSession().getId());
            exchange.getResponseHeaders().put(HttpString.tryFromString("TlsSessionId"), sessionId);
            if (!used.getAndSet(true)) {
                OutputStream out = exchange.getOutputStream();
                for (int i = 0; i < responseBytes; i++) {
                    out.write(7);
                    // Flush + pause 100ms every 1M for a response time of 30 seconds
                    if (i % (1024 * 1024) == 0) {
                        out.flush();
                        Thread.sleep(100);
                    }
                }
            }
        }));
        try {
            String uri = "https://localhost:" + getPort(server);
            ClientConfiguration conf = TestConfigurations.create(uri);
            Meter closedConns = DialogueClientMetrics.of(conf.taggedMetricRegistry())
                    .connectionClosedPartiallyConsumedResponse("client");
            String firstSession;
            Channel channel;
            assertThat(closedConns.getCount()).isZero();
            try (ApacheHttpClientChannels.CloseableClient client =
                    ApacheHttpClientChannels.createCloseableHttpClient(conf, "client")) {
                channel = ApacheHttpClientChannels.createSingleUri(uri, client);
                ListenableFuture<Response> response =
                        channel.execute(TestEndpoint.POST, Request.builder().build());
                Response resp = response.get();
                firstSession = resp.getFirstHeader("TlsSessionId").orElseThrow();
                assertThat(resp.code()).isEqualTo(200);
                try (InputStream responseStream = resp.body()) {
                    assertThat(responseStream.read()).isEqualTo(7);
                    long beforeClose = System.nanoTime();
                    closeType.close(responseStream, resp);
                    Duration closeDuration = Duration.ofNanos(System.nanoTime() - beforeClose);
                    assertThat(closeDuration).isLessThan(Duration.ofSeconds(2));
                    assertThat(closedConns.getCount()).isOne();
                }
            }
            // Ensure that the client isn't left in a bad state (connection has been discarded, not incorrectly added
            // back to the pool in an ongoing request state)
            ListenableFuture<Response> response =
                    channel.execute(TestEndpoint.POST, Request.builder().build());
            try (Response resp = response.get()) {
                assertThat(resp.code()).isEqualTo(200);
                assertThat(resp.body()).isEmpty();
                assertThat(resp.getFirstHeader("TlsSessionId").orElseThrow())
                        .as("Expected a new connection")
                        .isNotEqualTo(firstSession);
                assertThat(closedConns.getCount()).isOne();
            }
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @MethodSource("responseTestArguments")
    public void small_response(CloseType closeType, TransferEncoding encoding) throws Exception {
        Undertow server = startServer(new BlockingHandler(exchange -> {
            long responseBytes = 300;
            encoding.apply(exchange, responseBytes);
            OutputStream out = exchange.getOutputStream();
            for (int i = 0; i < responseBytes; i++) {
                out.write(7);
                out.flush();
            }
        }));
        try {
            String uri = "https://localhost:" + getPort(server);
            ClientConfiguration conf = TestConfigurations.create(uri);
            Meter closedConns = DialogueClientMetrics.of(conf.taggedMetricRegistry())
                    .connectionClosedPartiallyConsumedResponse("client");
            assertThat(closedConns.getCount()).isZero();
            try (ApacheHttpClientChannels.CloseableClient client =
                    ApacheHttpClientChannels.createCloseableHttpClient(conf, "client")) {
                Channel channel = ApacheHttpClientChannels.createSingleUri(uri, client);
                ListenableFuture<Response> response =
                        channel.execute(TestEndpoint.POST, Request.builder().build());
                Response resp = response.get();
                assertThat(resp.code()).isEqualTo(200);
                try (InputStream responseStream = resp.body()) {
                    assertThat(responseStream.read()).isEqualTo(7);
                    long beforeClose = System.nanoTime();
                    closeType.close(responseStream, resp);
                    Duration closeDuration = Duration.ofNanos(System.nanoTime() - beforeClose);
                    assertThat(closeDuration).isLessThan(Duration.ofSeconds(2));
                    assertThat(closedConns.getCount())
                            .as("Small responses below the threshold should not trigger closure")
                            .isZero();
                }
            }
        } finally {
            server.stop();
        }
    }

    private static Stream<Arguments> responseTestArguments() {
        return Arrays.stream(CloseType.values()).flatMap(closeType -> Arrays.stream(TransferEncoding.values())
                .map(trasnferEncoding -> Arguments.of(closeType, trasnferEncoding)));
    }

    private static Undertow startServer(HttpHandler handler) {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        Undertow server = Undertow.builder()
                .setHandler(handler)
                .addHttpsListener(0, null, sslContext)
                .build();
        server.start();
        return server;
    }

    private static int getPort(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }

    enum CloseType {
        STREAM() {
            @Override
            void close(InputStream stream, Response _response) throws IOException {
                stream.close();
            }
        },
        RESPONSE() {
            @Override
            void close(InputStream _stream, Response response) {
                response.close();
            }
        };

        abstract void close(InputStream stream, Response response) throws IOException;
    }

    enum TransferEncoding {
        CONTENT_LENGTH() {
            @Override
            void apply(HttpServerExchange exchange, long responseBytes) {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, Long.toString(responseBytes));
            }
        },
        CHUNKED() {
            @Override
            void apply(HttpServerExchange _exchange, long _responseBytes) {}
        };

        abstract void apply(HttpServerExchange exchange, long responseBytes);
    }
}
