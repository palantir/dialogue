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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.callcontext.CallContext;
import com.palantir.callcontext.CallContextScope;
import com.palantir.callcontext.Deadlines;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines.Enforcement;
import com.palantir.deadlines.Deadlines.RequestDecodingAdapter;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a deadline bound only in the {@link CallContext} (not in trace state) applies to requests, including
 * when Dialogue dispatches them from a thread other than the caller's.
 */
@SuppressWarnings("FutureReturnValueIgnored")
final class CallContextDeadlinesTest {

    private static final Duration CONTEXT_DEADLINE = Duration.ofSeconds(1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Nested
    final class DeadlineAdvertisement {

        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final Channel channel = DeadlineAdvertisementChannel.create(
                (_endpoint, request) -> {
                    requests.add(request);
                    return Futures.immediateCancelledFuture();
                },
                READ_TIMEOUT,
                Optional.of(true));

        @Test
        void encodes_call_context_deadline() {
            try (CallContextScope ignored = Deadlines.withDeadline(CONTEXT_DEADLINE)) {
                channel.execute(TestEndpoint.GET, Request.builder().build());
            }

            assertThat(requests)
                    .singleElement()
                    .satisfies(request ->
                            assertThat(expectWithin(request)).isPositive().isLessThanOrEqualTo(CONTEXT_DEADLINE));
        }

        @Test
        void call_context_without_deadline_overrides_trace_deadline() {
            try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
                parseTraceDeadline("1");
                try (CallContextScope ignoredScope = Deadlines.withoutDeadline()) {
                    channel.execute(TestEndpoint.GET, Request.builder().build());
                }
            }

            assertThat(requests)
                    .singleElement()
                    .satisfies(request -> assertThat(expectWithin(request)).isEqualTo(READ_TIMEOUT));
        }

        @Test
        void falls_back_to_trace_deadline_without_call_context_deadline() {
            try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
                parseTraceDeadline("1");
                channel.execute(TestEndpoint.GET, Request.builder().build());
            }

            assertThat(requests)
                    .singleElement()
                    .satisfies(request ->
                            assertThat(expectWithin(request)).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(1)));
        }

        @Test
        void expired_call_context_deadline_fails_without_sending() {
            CallContext context = requestContext("0");
            ListenableFuture<Response> response;
            try (CallContextScope ignored = context.attach()) {
                response = channel.execute(TestEndpoint.GET, Request.builder().build());
            }

            assertThat(response)
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .havingCause()
                    .isInstanceOf(DeadlineExpiredException.class);
            assertThat(requests).isEmpty();
        }

        @Test
        void disarmed_request_deadline_is_not_encoded() {
            CallContext context = requestContext("1");
            Deadlines.disarmRequestDeadline(context);
            try (CallContextScope ignored = context.attach()) {
                channel.execute(TestEndpoint.GET, Request.builder().build());
            }

            assertThat(requests)
                    .singleElement()
                    .satisfies(request -> assertThat(expectWithin(request)).isEqualTo(READ_TIMEOUT));
        }
    }

    @Nested
    final class Queued {

        @Test
        void queued_request_keeps_call_context_when_drained_by_another_request() {
            SettableFuture<Response> firstResponse = SettableFuture.create();
            AtomicBoolean permitAvailable = new AtomicBoolean(true);
            AtomicInteger calls = new AtomicInteger();
            List<Optional<Duration>> dispatchedDeadlines = new CopyOnWriteArrayList<>();
            LimitedChannel delegate = (_endpoint, _request, _limitEnforcement) -> {
                if (!permitAvailable.get()) {
                    return Optional.empty();
                }
                dispatchedDeadlines.add(Deadlines.getRemainingDeadline());
                return Optional.of(calls.getAndIncrement() == 0 ? firstResponse : Futures.immediateCancelledFuture());
            };
            QueuedChannel queue = new QueuedChannel(
                    delegate,
                    "my-channel",
                    "queue-type",
                    QueuedChannel.channelInstrumentation(
                            DialogueClientMetrics.of(new DefaultTaggedMetricRegistry()), "my-channel"),
                    100,
                    OptionalLong.empty(),
                    Ticker.systemTicker(),
                    null);

            queue.execute(TestEndpoint.GET, Request.builder().build());
            permitAvailable.set(false);
            ListenableFuture<Response> queued;
            try (CallContextScope ignored = Deadlines.withDeadline(CONTEXT_DEADLINE)) {
                queued = queue.execute(TestEndpoint.GET, Request.builder().build());
            }
            assertThat(queued).isNotDone();

            // The first request completes outside the caller's scope, which drains the queue on this thread
            permitAvailable.set(true);
            firstResponse.set(new TestResponse().code(200));

            assertThat(queued).isDone();
            assertThat(dispatchedDeadlines).hasSize(2);
            assertThat(dispatchedDeadlines.get(0))
                    .as("first request has no deadline")
                    .isEmpty();
            assertThat(dispatchedDeadlines.get(1))
                    .as("queued request runs with its caller's deadline")
                    .hasValueSatisfying(remaining -> assertThat(remaining).isLessThanOrEqualTo(CONTEXT_DEADLINE));
        }
    }

    @Nested
    final class Retrying {

        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        @AfterEach
        void afterEach() {
            scheduler.shutdownNow();
        }

        @Test
        void retry_without_backoff_keeps_call_context() {
            SettableFuture<Response> firstAttempt = SettableFuture.create();
            List<Optional<Duration>> attemptDeadlines = new CopyOnWriteArrayList<>();
            AtomicInteger attempts = new AtomicInteger();
            EndpointChannel delegate = _request -> {
                attemptDeadlines.add(Deadlines.getRemainingDeadline());
                return attempts.getAndIncrement() == 0
                        ? firstAttempt
                        : Futures.immediateFuture(new TestResponse().code(200));
            };
            EndpointChannel retrying = retryingChannel(delegate, Duration.ZERO);

            ListenableFuture<Response> response;
            try (CallContextScope ignored = Deadlines.withDeadline(CONTEXT_DEADLINE)) {
                response = retrying.execute(Request.builder().build());
            }
            // The retry runs on the thread that completes the failed attempt, outside the caller's scope
            firstAttempt.set(new TestResponse().code(503));

            assertThat(response).isDone();
            assertThat(attemptDeadlines)
                    .hasSize(2)
                    .allSatisfy(remaining -> assertThat(remaining)
                            .hasValueSatisfying(value -> assertThat(value).isLessThanOrEqualTo(CONTEXT_DEADLINE)));
        }

        @Test
        void retry_with_backoff_keeps_call_context() {
            List<Optional<Duration>> attemptDeadlines = new CopyOnWriteArrayList<>();
            AtomicInteger attempts = new AtomicInteger();
            EndpointChannel delegate = _request -> {
                attemptDeadlines.add(Deadlines.getRemainingDeadline());
                return Futures.immediateFuture(new TestResponse().code(attempts.getAndIncrement() == 0 ? 503 : 200));
            };
            EndpointChannel retrying = retryingChannel(delegate, Duration.ofMillis(1));

            ListenableFuture<Response> response;
            try (CallContextScope ignored = Deadlines.withDeadline(CONTEXT_DEADLINE)) {
                response = retrying.execute(Request.builder().build());
            }

            // The retry runs on the retry scheduler
            assertThat(response).succeedsWithin(Duration.ofSeconds(5));
            assertThat(attemptDeadlines)
                    .hasSize(2)
                    .allSatisfy(remaining -> assertThat(remaining)
                            .hasValueSatisfying(value -> assertThat(value).isLessThanOrEqualTo(CONTEXT_DEADLINE)));
        }

        private EndpointChannel retryingChannel(EndpointChannel delegate, Duration backoffSlotSize) {
            return new RetryingChannel(
                    delegate,
                    TestEndpoint.GET,
                    "my-channel",
                    new DefaultTaggedMetricRegistry(),
                    1,
                    backoffSlotSize,
                    ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                    ClientConfiguration.RetryOnTimeout.DISABLED,
                    scheduler,
                    () -> 1.0);
        }
    }

    @Nested
    final class Sticky {

        @Test
        void sticky_request_waiting_on_first_request_keeps_call_context() {
            SettableFuture<Response> firstResponse = SettableFuture.create();
            List<Optional<Duration>> dispatchedDeadlines = new CopyOnWriteArrayList<>();
            AtomicInteger calls = new AtomicInteger();
            EndpointChannel endpointChannel = _request -> {
                dispatchedDeadlines.add(Deadlines.getRemainingDeadline());
                return calls.getAndIncrement() == 0 ? firstResponse : Futures.immediateCancelledFuture();
            };
            Channel sticky = stickyChannel(endpointChannel);

            sticky.execute(TestEndpoint.GET, Request.builder().build());
            ListenableFuture<Response> waiting;
            try (CallContextScope ignored = Deadlines.withDeadline(CONTEXT_DEADLINE)) {
                waiting = sticky.execute(TestEndpoint.GET, Request.builder().build());
            }
            assertThat(waiting).isNotDone();

            // The waiting request runs on the thread that completes the first request, outside the caller's scope
            firstResponse.setException(new RuntimeException("first request failed"));

            assertThat(waiting).isDone();
            assertThat(dispatchedDeadlines).hasSize(2);
            assertThat(dispatchedDeadlines.get(0))
                    .as("first request has no deadline")
                    .isEmpty();
            assertThat(dispatchedDeadlines.get(1))
                    .as("waiting request runs with its caller's deadline")
                    .hasValueSatisfying(remaining -> assertThat(remaining).isLessThanOrEqualTo(CONTEXT_DEADLINE));
        }

        private Channel stickyChannel(EndpointChannel endpointChannel) {
            Config config = mock(Config.class);
            ClientConfiguration clientConfiguration = mock(ClientConfiguration.class);
            when(config.channelName()).thenReturn("channel");
            when(config.clientConf()).thenReturn(clientConfiguration);
            when(clientConfiguration.taggedMetricRegistry()).thenReturn(new DefaultTaggedMetricRegistry());
            EndpointChannelFactory endpointChannelFactory = mock(EndpointChannelFactory.class);
            when(endpointChannelFactory.endpoint(any())).thenReturn(endpointChannel);
            return StickyEndpointChannels2.create(config, mock(LimitedChannel.class), endpointChannelFactory)
                    .get();
        }
    }

    private static CallContext requestContext(String expectWithin) {
        return Deadlines.withRequestDeadline(
                CallContext.empty(),
                Optional.empty(),
                Map.of("Expect-Within", expectWithin),
                MapDecoder.INSTANCE,
                Enforcement.ENFORCE);
    }

    private static void parseTraceDeadline(String expectWithin) {
        com.palantir.deadlines.Deadlines.parseFromRequest(
                Optional.empty(), Map.of("Expect-Within", expectWithin), MapDecoder.INSTANCE, Enforcement.DEFER);
    }

    private static Duration expectWithin(Request request) {
        List<String> values = request.headerParams().get("Expect-Within");
        assertThat(values).hasSize(1);
        return Duration.ofNanos((long) (Double.parseDouble(values.get(0)) * 1e9d));
    }

    private enum MapDecoder implements RequestDecodingAdapter<Map<String, String>> {
        INSTANCE;

        @Override
        public Optional<String> getFirstHeader(Map<String, String> headers, String headerName) {
            return Optional.ofNullable(headers.get(headerName));
        }
    }
}
