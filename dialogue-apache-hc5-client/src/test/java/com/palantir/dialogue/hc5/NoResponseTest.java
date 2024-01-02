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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.xnio.IoUtils;

public final class NoResponseTest {

    private static Channel create(ClientConfiguration config) {
        return ApacheHttpClientChannels.create(config, "test");
    }

    private static final Request request = Request.builder().build();

    private static int getPort(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }

    private static Undertow createStarted(HttpHandler handler) {
        Undertow server = Undertow.builder()
                .setHandler(handler)
                .addHttpsListener(0, null, SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG))
                .build();
        server.start();
        return server;
    }

    @Test
    public void testConnectionClosedAfterDelay() {
        // This test closes the connection after taking some time to "process"
        // the request. We expect the client _not_ to retry in this case.
        AtomicInteger requests = new AtomicInteger();
        Undertow server = createStarted(new BlockingHandler(exchange -> {
            requests.incrementAndGet();
            Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(5));
            exchange.getConnection().close();
        }));
        try {
            Channel channel = create(defaultClientConfig(getPort(server)));
            ListenableFuture<Response> response = channel.execute(TestEndpoint.POST, request);
            assertThatThrownBy(() -> response.get()).hasCauseInstanceOf(SocketTimeoutException.class);
            assertThat(requests).as("Request mustn't be retried").hasValue(1);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testIdleConnectionClosed() throws Exception {
        // Pooled connection should be reused, and retried if they've
        // been timed out. This test immediately closes keep-alive connections
        // on the server side to validate that clients are able to retry
        // after the next request fails. This requires at least two
        // requests to ensure an idle connection is reused.
        Undertow server = createStarted(new BlockingHandler(exchange -> {
            exchange.setStatusCode(200);
            OutputStream respBody = exchange.getOutputStream();
            // Flush data to commit a response.
            respBody.write(1);
            respBody.flush();
            // Close the 'idle' connection after this returns.
            exchange.addExchangeCompleteListener((exc, nextListener) -> {
                IoUtils.safeClose(exc.getConnection());
                nextListener.proceed();
            });
        }));
        try {
            Channel channel = create(defaultClientConfig(getPort(server)));
            for (int i = 0; i < 10; i++) {
                assertSuccessfulRequest(channel);
            }
        } finally {
            server.stop();
        }
    }

    private static ClientConfiguration defaultClientConfig(int port) {
        return ClientConfigurations.of(
                ImmutableList.of("https://localhost:" + port),
                SslSocketFactories.createSslSocketFactory(TestConfigurations.SSL_CONFIG),
                SslSocketFactories.createX509TrustManager(TestConfigurations.SSL_CONFIG),
                TestConfigurations.AGENT);
    }

    private static void assertSuccessfulRequest(Channel channel) throws Exception {
        try (Response response = channel.execute(TestEndpoint.POST, request).get()) {
            assertThat(response.code()).isEqualTo(200);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
