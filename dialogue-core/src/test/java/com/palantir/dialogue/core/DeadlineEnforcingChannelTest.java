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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.tracing.CloseableTracer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DeadlineEnforcingChannelTest {

    private static final Request REQUEST = Request.builder().build();

    private ScheduledExecutorService scheduler;

    @BeforeEach
    void beforeEach() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void afterEach() {
        scheduler.shutdownNow();
    }

    private DeadlineEnforcingChannel create(EndpointChannel delegate) {
        return new DeadlineEnforcingChannel(delegate, scheduler);
    }

    /** Sets a deadline on the current trace with the given remaining duration. Requires an active trace. */
    private static void setDeadline(Duration remaining) {
        Deadlines.parseFromRequest(
                Optional.of(remaining),
                REQUEST,
                new Deadlines.RequestDecodingAdapter<>() {
                    @Override
                    public Optional<String> getFirstHeader(Request _request, String _headerName) {
                        return Optional.empty();
                    }
                },
                Deadlines.Enforcement.ENFORCE);
    }

    @Nested
    class NoDeadline {

        @Test
        void delegates_directly_when_no_deadline_set() throws ExecutionException, InterruptedException {
            try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
                TestResponse expected = new TestResponse().code(200);
                DeadlineEnforcingChannel channel = create(_request -> Futures.immediateFuture(expected));

                ListenableFuture<Response> result = channel.execute(REQUEST);
                assertThat(result).isDone();
                assertThat(result.get().code()).isEqualTo(200);
            }
        }
    }

    @Nested
    class DeadlineAlreadyExpired {

        @Test
        void fails_immediately_when_deadline_already_expired() throws Exception {
            try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
                setDeadline(Duration.ofMillis(1));
                Thread.sleep(10); // let it expire

                EndpointChannel delegate = _request -> {
                    throw new AssertionError("Delegate should not be called");
                };

                DeadlineEnforcingChannel channel = create(delegate);
                ListenableFuture<Response> result = channel.execute(REQUEST);

                assertThat(result).isDone();
                assertThat(result)
                        .failsWithin(Duration.ZERO)
                        .withThrowableThat()
                        .withCauseInstanceOf(DeadlineExpiredException.class);
            }
        }
    }

    @Nested
    class DeadlineExpiresBeforeResponse {

        @Test
        void caller_gets_deadline_exception() throws Exception {
            try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
                setDeadline(Duration.ofMillis(100));

                SettableFuture<Response> delegateFuture = SettableFuture.create();
                DeadlineEnforcingChannel channel = create(_request -> delegateFuture);

                ListenableFuture<Response> result = channel.execute(REQUEST);
                assertThat(result).isNotDone();

                Thread.sleep(200);

                assertThat(result).isDone();
                assertThat(result)
                        .failsWithin(Duration.ZERO)
                        .withThrowableThat()
                        .withCauseInstanceOf(DeadlineExpiredException.class);
            }
        }

        @Test
        void delegate_future_is_cancelled_when_deadline_expires() throws Exception {
            try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
                setDeadline(Duration.ofMillis(100));

                SettableFuture<Response> delegateFuture = SettableFuture.create();
                DeadlineEnforcingChannel channel = create(_request -> delegateFuture);

                ListenableFuture<Response> result = channel.execute(REQUEST);

                Thread.sleep(200);
                assertThat(result).isDone();
                assertThat(result)
                        .failsWithin(Duration.ZERO)
                        .withThrowableThat()
                        .withCauseInstanceOf(DeadlineExpiredException.class);

                // The delegate future should be cancelled to free the connection
                // and stop any in-flight retries
                assertThat(delegateFuture)
                        .as("Delegate future must be cancelled when deadline fires "
                                + "to free the HTTP connection and stop retries")
                        .isCancelled();
            }
        }
    }

    @Nested
    class ResponseArrivesBeforeDeadline {

        @Test
        void caller_gets_response() throws ExecutionException, InterruptedException {
            try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
                setDeadline(Duration.ofSeconds(5));

                SettableFuture<Response> delegateFuture = SettableFuture.create();
                DeadlineEnforcingChannel channel = create(_request -> delegateFuture);

                ListenableFuture<Response> result = channel.execute(REQUEST);
                assertThat(result).isNotDone();

                TestResponse wireResponse = new TestResponse().code(200);
                delegateFuture.set(wireResponse);

                assertThat(result).isDone();
                assertThat(result.get().code()).isEqualTo(200);
                assertThat(wireResponse.isClosed())
                        .as("Response should NOT be closed — caller owns it")
                        .isFalse();
            }
        }
    }

    @Nested
    class CallerCancellation {

        @Test
        void cancel_propagates_to_delegate() {
            try (CloseableTracer tracer = CloseableTracer.startSpan("test")) {
                setDeadline(Duration.ofSeconds(5));

                SettableFuture<Response> delegateFuture = SettableFuture.create();
                DeadlineEnforcingChannel channel = create(_request -> delegateFuture);

                ListenableFuture<Response> result = channel.execute(REQUEST);
                assertThat(result).isNotDone();

                result.cancel(true);
                assertThat(result).isCancelled();
                assertThat(delegateFuture).isCancelled();
            }
        }
    }
}
