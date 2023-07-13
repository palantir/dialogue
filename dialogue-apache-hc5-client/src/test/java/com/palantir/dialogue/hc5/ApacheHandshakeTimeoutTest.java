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

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.logsafe.Preconditions;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

public final class ApacheHandshakeTimeoutTest {

    private Channel create(ClientConfiguration config) {
        return ApacheHttpClientChannels.create(config, "test");
    }

    private static final Request request = Request.builder()
            .body(new RequestBody() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    output.write("Hello, World".getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String contentType() {
                    return "text/plain";
                }

                @Override
                public boolean repeatable() {
                    return true;
                }

                @Override
                public void close() {}
            })
            .build();

    private DelayingNextTaskExecutorService executor;
    private XnioWorker worker;
    private Undertow server;

    private static int getPort(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }

    @BeforeEach
    public void beforeEach() {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        Xnio xnio = Xnio.getInstance(Undertow.class.getClassLoader());
        // This test operates based on the understanding that handshakes complete by executing
        // the Runnable from SSLEngine.getDelegatedTask on the worker executor. Each handshake
        // executes at least one of these tasks, and we can prolong the handshake by introducing
        // a delay.
        executor = new DelayingNextTaskExecutorService(Executors.newCachedThreadPool());
        worker = xnio.createWorkerBuilder()
                .setWorkerIoThreads(1)
                .setExternalExecutorService(executor)
                .build();
        server = Undertow.builder()
                .setWorker(worker)
                .setHandler(ResponseCodeHandler.HANDLE_200)
                .addHttpsListener(0, null, sslContext)
                .build();
        server.start();
    }

    @AfterEach
    public void afterEach() {
        server.stop();
        worker.shutdownNow();
        assertThat(MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(5)))
                .isTrue();
    }

    @Test
    public void testHandshakeTimeoutFailure() throws Exception {
        int serverPort = getPort(server);
        ClientConfiguration noRetryConfig = ClientConfiguration.builder()
                .from(TestConfigurations.create("https://localhost:" + serverPort))
                .connectTimeout(Duration.ofMillis(500))
                .readTimeout(Duration.ofMillis(500))
                .writeTimeout(Duration.ofMillis(500))
                .maxNumRetries(0)
                .build();
        Channel noRetryChannel = create(noRetryConfig);

        executor.delayNextTask(Duration.ofSeconds(1));

        assertThatThrownBy(noRetryChannel.execute(TestEndpoint.POST, request)::get)
                .getCause()
                .satisfies(cause -> assertThat(cause)
                        .isInstanceOf(SafeConnectTimeoutException.class)
                        .as("Only IOExceptions are retried")
                        .isInstanceOf(IOException.class)
                        .as("SocketTimeoutExceptions cannot be retried")
                        .isNotInstanceOf(SocketTimeoutException.class));
    }

    @Test
    public void testHandshakeTimeoutIsRetried() throws Exception {
        int serverPort = getPort(server);
        ClientConfiguration retryingConfig = ClientConfiguration.builder()
                .from(TestConfigurations.create("https://localhost:" + serverPort))
                .connectTimeout(Duration.ofMillis(500))
                .readTimeout(Duration.ofMillis(500))
                .writeTimeout(Duration.ofMillis(500))
                .maxNumRetries(1)
                .build();

        Channel retryChannel = create(retryingConfig);
        executor.delayNextTask(Duration.ofSeconds(1));
        try (Response response =
                retryChannel.execute(TestEndpoint.POST, request).get()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    public void testHandshakeTimeoutIsRetriedWithNonRetryableBody() throws Exception {
        Request req = Request.builder()
                .body(new RequestBody() {
                    private boolean closed = false;
                    private boolean consumed = false;

                    @Override
                    public void writeTo(OutputStream output) throws IOException {
                        if (closed) {
                            throw new IllegalStateException("non-repeatable body already closed");
                        }
                        if (consumed) {
                            throw new IllegalStateException("non-repeatable body already consumed");
                        }
                        consumed = true;
                        output.write("Hello, World".getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public String contentType() {
                        return "text/plain";
                    }

                    @Override
                    public boolean repeatable() {
                        return false;
                    }

                    @Override
                    public void close() {
                        closed = true;
                    }
                })
                .build();

        int serverPort = getPort(server);
        ClientConfiguration retryingConfig = ClientConfiguration.builder()
                .from(TestConfigurations.create("https://localhost:" + serverPort))
                .connectTimeout(Duration.ofMillis(500))
                .readTimeout(Duration.ofMillis(500))
                .writeTimeout(Duration.ofMillis(500))
                .maxNumRetries(1)
                .build();

        Channel retryChannel = create(retryingConfig);
        executor.delayNextTask(Duration.ofSeconds(1));
        // The first request will fail because the timeout will be exceeded. There has to be a retry for the request to
        // succeed.
        try (Response response = retryChannel.execute(TestEndpoint.POST, req).get()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    public void testHandshakeLongerThanConnectDoesNotTimeout() throws Exception {
        int serverPort = getPort(server);
        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create("https://localhost:" + serverPort))
                .connectTimeout(Duration.ofMillis(500))
                .readTimeout(Duration.ofSeconds(2))
                .writeTimeout(Duration.ofMillis(2))
                .maxNumRetries(0)
                .build();

        Channel channel = create(config);
        executor.delayNextTask(Duration.ofSeconds(1));
        try (Response response = channel.execute(TestEndpoint.POST, request).get()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }

    private static final class DelayingNextTaskExecutorService extends ForwardingExecutorService {

        private final ExecutorService delegate;
        private final AtomicReference<Duration> nextTaskDelay = new AtomicReference<>();

        DelayingNextTaskExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        void delayNextTask(Duration duration) {
            Preconditions.checkState(nextTaskDelay.compareAndSet(null, duration), "nextTaskDelay is already set");
        }

        @Override
        public void execute(Runnable command) {
            Runnable toExecute = command;
            Duration delay = nextTaskDelay.getAndSet(null);
            if (delay != null) {
                toExecute = () -> {
                    Uninterruptibles.sleepUninterruptibly(delay);
                    command.run();
                };
            }

            delegate().execute(toExecute);
        }

        @Override
        protected ExecutorService delegate() {
            return delegate;
        }
    }
}
