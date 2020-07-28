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

package com.palantir.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ForwardingExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeLoggable;
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
import org.junit.jupiter.api.Test;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

public abstract class AbstractHandshakeTimeoutTest {

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

    protected abstract Channel create(ClientConfiguration config);

    private static int getPort(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }

    @Test
    public void testHandshakeTimeout() throws Exception {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        Xnio xnio = Xnio.getInstance(Undertow.class.getClassLoader());
        DelayingNextTaskExecutorService executor = new DelayingNextTaskExecutorService(Executors.newCachedThreadPool());
        XnioWorker worker = xnio.createWorkerBuilder()
                .setWorkerIoThreads(1)
                .setExternalExecutorService(executor)
                .build();
        Undertow server = Undertow.builder()
                .setWorker(worker)
                .setHandler(ResponseCodeHandler.HANDLE_200)
                .addHttpsListener(0, null, sslContext)
                .build();
        server.start();
        try {
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
                            .isInstanceOf(SafeLoggable.class)
                            .isInstanceOf(IOException.class)
                            .isNotInstanceOf(SocketTimeoutException.class)
                            .extracting(value -> value.getClass().getSimpleName())
                            .isEqualTo("SafeConnectTimeoutException"));

            ClientConfiguration retryConfig = ClientConfiguration.builder()
                    .from(noRetryConfig)
                    .maxNumRetries(1)
                    .build();
            Channel retryChannel = create(retryConfig);
            executor.delayNextTask(Duration.ofSeconds(1));
            try (Response response =
                    retryChannel.execute(TestEndpoint.POST, request).get()) {
                assertThat(response.code()).isEqualTo(200);
            }
        } finally {
            server.stop();
            worker.shutdownNow();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(5)))
                    .isTrue();
        }
    }

    @Test
    public void testHandshakeLongerThanConnectDoesNotTimeout() throws Exception {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        Xnio xnio = Xnio.getInstance(Undertow.class.getClassLoader());
        DelayingNextTaskExecutorService executor = new DelayingNextTaskExecutorService(Executors.newCachedThreadPool());
        XnioWorker worker = xnio.createWorkerBuilder()
                .setWorkerIoThreads(1)
                .setExternalExecutorService(executor)
                .build();
        Undertow server = Undertow.builder()
                .setWorker(worker)
                .setHandler(ResponseCodeHandler.HANDLE_200)
                .addHttpsListener(0, null, sslContext)
                .build();
        server.start();
        try {
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
        } finally {
            server.stop();
            worker.shutdownNow();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(5)))
                    .isTrue();
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
