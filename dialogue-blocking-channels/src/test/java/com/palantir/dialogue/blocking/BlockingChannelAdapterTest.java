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
package com.palantir.dialogue.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BlockingChannelAdapterTest {

    private final Response stubResponse = new TestResponse().code(200);

    private ExecutorService executor;

    @BeforeEach
    public void beforeEach() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    public void afterEach() {
        assertThat(MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(3)))
                .isTrue();
    }

    @Test
    public void testSuccessful() {
        CountDownLatch latch = new CountDownLatch(1);
        Channel channel = BlockingChannelAdapter.of(
                (_endpoint, _request) -> {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return stubResponse;
                },
                executor);
        ListenableFuture<Response> result =
                channel.execute(TestEndpoint.POST, Request.builder().build());
        assertThat(result).isNotDone();
        latch.countDown();
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(result).isDone();
            assertThat(result.get()).isSameAs(stubResponse);
        });
    }

    @Test
    public void testFailure() {
        Channel channel = BlockingChannelAdapter.of(
                (_endpoint, _request) -> {
                    throw new SafeRuntimeException("expected");
                },
                executor);
        ListenableFuture<Response> result =
                channel.execute(TestEndpoint.POST, Request.builder().build());
        Awaitility.waitAtMost(Duration.ofSeconds(3)).until(result::isDone);
        assertThatThrownBy(result::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseExactlyInstanceOf(SafeRuntimeException.class)
                .hasRootCauseMessage("expected");
    }

    @Test
    public void testCancel() throws InterruptedException {
        CountDownLatch channelLatch = new CountDownLatch(1);
        CountDownLatch returnLatch = new CountDownLatch(1);
        AtomicBoolean invocationInterrupted = new AtomicBoolean();
        Response response = mock(Response.class);
        Channel channel = BlockingChannelAdapter.of(
                (_endpoint, _request) -> {
                    channelLatch.countDown();
                    Uninterruptibles.awaitUninterruptibly(returnLatch);
                    invocationInterrupted.set(Thread.currentThread().isInterrupted());
                    return response;
                },
                executor);
        ListenableFuture<Response> result =
                channel.execute(TestEndpoint.POST, Request.builder().build());
        channelLatch.await();
        assertThat(result.cancel(true)).isTrue();
        assertThat(result).isCancelled();
        // Allow the channel to complete
        returnLatch.countDown();
        Awaitility.waitAtMost(Duration.ofSeconds(3))
                .untilAsserted(() -> verify(response).close());
        assertThat(invocationInterrupted).isTrue();
    }

    @Test
    void testAlreadyShutdown() {
        executor.shutdown();
        BlockingChannel delegate = mock(BlockingChannel.class);
        Channel channel = BlockingChannelAdapter.of(delegate, executor);

        ListenableFuture<Response> future =
                channel.execute(TestEndpoint.POST, Request.builder().build());

        assertThat(future).isDone();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RejectedExecutionException.class);
    }
}
