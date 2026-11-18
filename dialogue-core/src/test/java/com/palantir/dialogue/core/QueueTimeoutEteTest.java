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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for queue timeout using a real {@link DialogueChannel} pipeline with
 * controllable wire channels. Tests verify timeout behavior across both the channel-level
 * and endpoint-level queues, including shared expiration budget.
 */
class QueueTimeoutEteTest {

    private static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("foo", "1.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"), Paths.get("src/test/resources/keyStore.jks"), "keystore");
    private static final Endpoint ENDPOINT = TestEndpoint.POST;
    private static final String HOST_A = "http://hostA:8080";
    private static final String HOST_B = "http://hostB:8080";
    private static final String HOST_C = "http://hostC:8080";
    private static final Duration QUEUE_TIMEOUT = Duration.ofSeconds(1);
    // The AIMD concurrency limit each host/endpoint starts at; sending this many concurrent requests saturates a
    // single host and forces the next request to queue.
    private static final int SATURATING_REQUESTS =
            (int) CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.INITIAL_LIMIT;

    // Consecutive dropped responses needed to drive an AIMD limit from its initial value down to the MIN_LIMIT floor.
    private static final int DROPS_TO_REACH_FLOOR = dropsToReachAimdFloor();

    private static int dropsToReachAimdFloor() {
        double ratio = CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.BACKOFF_RATIO;
        double limit = CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.INITIAL_LIMIT;
        int drops = 0;
        while (limit > CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.MIN_LIMIT) {
            // Note: this mirrors logic in LimitUpdater.DROPPED. If that logic is changed, this will need to be updated
            // as well.
            limit = Math.max(CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.MIN_LIMIT, Math.floor(limit * ratio));
            drops++;
        }
        return drops;
    }

    @Nested
    class ChannelQueueTimeout {

        @Test
        @SuppressWarnings("FutureReturnValueIgnored")
        void queued_requests_are_failed_after_timeout() {
            WireChannel wire = new WireChannel();
            DefaultTaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
            DialogueChannel channel = buildChannel(wire, metrics, HOST_A, HOST_B, HOST_C);
            Counter queueSize = DialogueClientMetrics.of(metrics).requestsQueued("test");

            // Saturate all three hosts' AIMD limits.
            for (int i = 0; i < 3 * SATURATING_REQUESTS; i++) {
                channel.execute(ENDPOINT, Request.builder().build());
            }
            assertThat(queueSize.getCount()).isEqualTo(0);

            // Queue 5 more — all hosts full
            List<ListenableFuture<Response>> queued = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ListenableFuture<Response> future =
                        channel.execute(ENDPOINT, Request.builder().build());
                assertThat(future).isNotDone();
                queued.add(future);
            }
            assertThat(queueSize.getCount()).isEqualTo(5);

            Awaitility.waitAtMost(QUEUE_TIMEOUT.multipliedBy(2))
                    .untilAsserted(() -> assertThat(queueSize.getCount()).isEqualTo(0));
            for (ListenableFuture<Response> future : queued) {
                assertThat(future)
                        .as("each queued request is failed with a queue timeout")
                        .failsWithin(Duration.ZERO)
                        .withThrowableThat()
                        .withCauseInstanceOf(QueueTimeoutException.class);
            }
        }

        @Test
        @SuppressWarnings("FutureReturnValueIgnored")
        void timeout_is_cancelled_when_request_is_dispatched() throws Exception {
            WireChannel wire = new WireChannel();
            DialogueChannel channel = DialogueChannel.builder()
                    .channelName("cancel-on-dispatch")
                    .clientConfiguration(singleHostConfig(HOST_A))
                    .factory(_args -> wire)
                    .maxQueueSize(100)
                    .queueTimeout(QUEUE_TIMEOUT)
                    .build();

            // Saturate the host-level limit.
            for (int i = 0; i < SATURATING_REQUESTS; i++) {
                channel.execute(ENDPOINT, Request.builder().build());
            }
            assertThat(wire.pending()).hasSize(SATURATING_REQUESTS);

            // The next request enters the channel queue
            ListenableFuture<Response> queued =
                    channel.execute(ENDPOINT, Request.builder().build());
            assertThat(queued).isNotDone();
            assertThat(wire.pending()).as("Queued, not on wire").hasSize(SATURATING_REQUESTS);

            // Complete one -> frees a permit -> queued request is dispatched (timeout cancelled)
            wire.completeOldest();
            assertThat(wire.pending())
                    .as("Queued request should have been dispatched")
                    .hasSize(SATURATING_REQUESTS);

            // Wait well past the original timeout (double it) so a non-cancelled timeout would have decisively
            // fired. The dispatched request is wired to a never-completing wire future, so the only way it could
            // complete is an errant timeout.
            Thread.sleep(QUEUE_TIMEOUT.multipliedBy(2).toMillis());

            // The dispatched request should NOT have timed out — timeout was cancelled on dispatch
            assertThat(queued)
                    .as("Timeout should have been cancelled on dispatch")
                    .isNotDone();
        }
    }

    @Nested
    class EndpointQueueTimeout {

        @Test
        void request_times_out_in_endpoint_queue() {
            // Single host, drive endpoint AIMD limit down with 429s
            ConcurrentLinkedQueue<SettableFuture<Response>> pending = new ConcurrentLinkedQueue<>();
            AtomicBoolean returnImmediate429 = new AtomicBoolean(true);
            Channel wire = (_endpoint, _request) -> {
                if (returnImmediate429.get()) {
                    return Futures.immediateFuture(new TestResponse().code(429));
                }
                SettableFuture<Response> future = SettableFuture.create();
                pending.add(future);
                return future;
            };

            DefaultTaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
            DialogueChannel channel = DialogueChannel.builder()
                    .channelName("endpoint-timeout")
                    .clientConfiguration(singleHostConfig(HOST_A, metrics))
                    .factory(_args -> wire)
                    .maxQueueSize(100)
                    .queueTimeout(QUEUE_TIMEOUT)
                    .build();

            // Drive the endpoint AIMD limit down to its MIN_LIMIT floor with a run of non-custom 429s.
            for (int i = 0; i < DROPS_TO_REACH_FLOOR; i++) {
                ListenableFuture<Response> resp =
                        channel.execute(ENDPOINT, Request.builder().build());
                assertThat(resp).isDone();
            }

            returnImmediate429.set(false);

            // First request passes endpoint limiter (0 < 1), hits wire
            ListenableFuture<Response> request1 =
                    channel.execute(ENDPOINT, Request.builder().build());
            assertThat(request1).isNotDone();
            assertThat(pending).hasSize(1);

            // Second request: endpoint limiter full
            ListenableFuture<Response> request2 =
                    channel.execute(ENDPOINT, Request.builder().build());
            assertThat(request2).isNotDone();
            assertThat(pending).as("request2 is in endpoint queue, not on wire").hasSize(1);

            // Confirm via the metric that request2 sits in the endpoint queue specifically, not the host queue.
            Counter endpointQueued = DialogueClientMetrics.of(metrics)
                    .requestsEndpointQueued()
                    .channelName("endpoint-timeout")
                    .serviceName(ENDPOINT.serviceName())
                    .endpoint(ENDPOINT.endpointName())
                    .build();
            assertThat(endpointQueued.getCount())
                    .as("request2 should be counted in the endpoint queue, not the host queue")
                    .isEqualTo(1);

            // The timeout task fails request2 and then decrements the endpoint queue size, both on the scheduler
            // thread. Since the decrement runs just after the failure, waiting for the queue size to return to 0 is
            // the strongest single signal that the timeout has fully fired; by the time it does, request2 is already
            // failed. (Waiting on request2 alone would be racy: it could be observed done while the timeout task is
            // between failing it and decrementing the size.)
            Awaitility.waitAtMost(QUEUE_TIMEOUT.multipliedBy(2))
                    .untilAsserted(() -> assertThat(endpointQueued.getCount()).isEqualTo(0));
            assertThat(request2)
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .withCauseInstanceOf(QueueTimeoutException.class);

            // request1 is still on the wire, unaffected
            assertThat(request1).isNotDone();
            pending.poll().set(new TestResponse().code(200));
            assertThat(request1).isDone();
        }
    }

    @Nested
    class TimeoutNotRetried {

        @Test
        @SuppressWarnings("FutureReturnValueIgnored")
        void timed_out_request_is_not_retried() {
            // Track how many times the wire is hit
            AtomicInteger wireHitCount = new AtomicInteger();
            WireChannel wire = new WireChannel() {
                @Override
                @NonNull
                public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                    wireHitCount.incrementAndGet();
                    return super.execute(endpoint, request);
                }
            };

            DefaultTaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
            ClientConfiguration config = ClientConfiguration.builder()
                    .from(ClientConfigurations.of(ServiceConfiguration.builder()
                            .addUris(HOST_A)
                            .security(SSL_CONFIG)
                            .build()))
                    .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                    .userAgent(USER_AGENT)
                    .taggedMetricRegistry(metrics)
                    .maxNumRetries(3)
                    // no backoff, so a retry would re-dispatch immediately
                    .backoffSlotSize(Duration.ZERO)
                    .build();

            DialogueChannel channel = DialogueChannel.builder()
                    .channelName("no-retry-on-timeout")
                    .clientConfiguration(config)
                    .factory(_args -> wire)
                    .maxQueueSize(100)
                    .queueTimeout(QUEUE_TIMEOUT)
                    .build();

            // Saturate the host-level limit so the next request must queue.
            for (int i = 0; i < SATURATING_REQUESTS; i++) {
                channel.execute(ENDPOINT, Request.builder().build());
            }
            int wireHitsAfterSaturation = wireHitCount.get();

            // Queue a request — it will time out while every permit is held.
            ListenableFuture<Response> queued =
                    channel.execute(ENDPOINT, Request.builder().build());

            // The queue-timeout failure is a SafeRuntimeException (not an IOException or QoS response), so
            // RetryingChannel does not retry it: the request fails outright. If it were retried, this future would
            // instead stay pending while the retried attempt re-queued, and this await would time out.
            Awaitility.waitAtMost(QUEUE_TIMEOUT.multipliedBy(2))
                    .untilAsserted(() -> assertThat(queued).isDone());
            assertThat(queued)
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .withCauseInstanceOf(QueueTimeoutException.class);

            for (int i = 0; i < SATURATING_REQUESTS; i++) {
                wire.completeOldest();
            }
            assertThat(wireHitCount.get())
                    .as("A queue timeout must not be retried, so freeing capacity yields no new wire hit")
                    .isEqualTo(wireHitsAfterSaturation);

            // Corroborate via the retry metric: RetryingChannel records no retry for the queue timeout.
            long retryCount = metrics.getMetrics().entrySet().stream()
                    .filter(entry -> entry.getKey().safeName().equals("dialogue.client.request.retry"))
                    .mapToLong(entry -> ((Meter) entry.getValue()).getCount())
                    .sum();
            assertThat(retryCount).as("queue timeouts are not retryable").isZero();
        }
    }

    @Nested
    class RetryResetsBudget {

        private static final Duration LONG_TIMEOUT = Duration.ofHours(1);

        @Test
        @SuppressWarnings("FutureReturnValueIgnored")
        void retried_request_gets_a_fresh_queue_timeout_budget() {
            ManualTicker ticker = new ManualTicker();
            AtomicInteger wireCalls = new AtomicInteger();
            Queue<SettableFuture<Response>> held = new ConcurrentLinkedQueue<>();
            Channel wire = (_endpoint, _request) -> {
                // The 21st call will be a 429 to trigger a retry. Every other call (the saturating requests, and the
                // retry if it re-dispatches) is held pending to keep host capacity occupied.
                if (wireCalls.incrementAndGet() == SATURATING_REQUESTS + 1) {
                    return Futures.immediateFuture(new TestResponse().code(429));
                }
                SettableFuture<Response> future = SettableFuture.create();
                held.add(future);
                return future;
            };

            ClientConfiguration config = ClientConfiguration.builder()
                    .from(ClientConfigurations.of(ServiceConfiguration.builder()
                            .addUris(HOST_A)
                            .security(SSL_CONFIG)
                            .build()))
                    .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                    .userAgent(USER_AGENT)
                    .maxNumRetries(3)
                    // Zero backoff so the retry re-executes synchronously when the 429 is observed.
                    .backoffSlotSize(Duration.ZERO)
                    .build();

            DialogueChannel channel = DialogueChannel.builder()
                    .channelName("retry-resets-budget")
                    .clientConfiguration(config)
                    .factory(_args -> wire)
                    .maxQueueSize(100)
                    .queueTimeout(LONG_TIMEOUT)
                    .ticker(ticker)
                    .build();

            // Saturate the host-level AIMD limit so the next request must queue.
            for (int i = 0; i < SATURATING_REQUESTS; i++) {
                channel.execute(ENDPOINT, Request.builder().build());
            }

            // The request we're testing queues at ticker=0, stamping expiration = 0 + LONG_TIMEOUT.
            Request retried = Request.builder().build();
            ListenableFuture<Response> future = channel.execute(ENDPOINT, retried);
            assertThat(future).isNotDone();
            assertThat(QueueTimeoutAttachments.getConfiguredExpiration(retried))
                    .as("first attempt stamps expiration at ticker.read() + timeout")
                    .isEqualTo(LONG_TIMEOUT.toNanos());

            // Advance partway into the budget before the retry
            Duration elapsedBeforeRetry = Duration.ofMinutes(30);
            ticker.advance(elapsedBeforeRetry);

            // Free one permit: the queued request dispatches, the wire returns 429, and RetryingChannel retries
            // synchronously. The host stays saturated (the 429 drops the AIMD limit below the in-flight count), so the
            // retry re-queues and, because attempt 1's expiration was cleared on completion, it *should* stamp a
            // new expiration from the current (advanced 30m) clock.
            held.poll().set(new TestResponse().code(200));

            assertThat(future).as("the request is retried").isNotDone();
            assertThat(QueueTimeoutAttachments.getConfiguredExpiration(retried))
                    .as("retry re-queues with a fresh budget", elapsedBeforeRetry, LONG_TIMEOUT)
                    .isEqualTo(elapsedBeforeRetry.plus(LONG_TIMEOUT).toNanos());
        }
    }

    // Ticker whose time only advances when explicitly told to.
    private static final class ManualTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }

    private static class WireChannel implements Channel {
        private final Queue<SettableFuture<Response>> pendingResponses = new ConcurrentLinkedQueue<>();

        @Override
        public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
            SettableFuture<Response> future = SettableFuture.create();
            pendingResponses.add(future);
            return future;
        }

        void completeOldest() {
            SettableFuture<Response> future = pendingResponses.poll();
            assertThat(future).as("No pending requests").isNotNull();
            future.set(new TestResponse().code(200));
        }

        Queue<SettableFuture<Response>> pending() {
            return pendingResponses;
        }
    }

    private static DialogueChannel buildChannel(WireChannel wire, TaggedMetricRegistry metrics, String... uris) {
        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .uris(List.of(uris))
                        .security(SSL_CONFIG)
                        .build()))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .userAgent(USER_AGENT)
                .taggedMetricRegistry(metrics)
                .maxNumRetries(0)
                .backoffSlotSize(Duration.ZERO)
                .build();

        return DialogueChannel.builder()
                .channelName("test")
                .clientConfiguration(config)
                .factory(_args -> wire)
                .maxQueueSize(100)
                .queueTimeout(QUEUE_TIMEOUT)
                .build();
    }

    private static ClientConfiguration singleHostConfig(String uri) {
        return singleHostConfig(uri, new DefaultTaggedMetricRegistry());
    }

    private static ClientConfiguration singleHostConfig(String uri, TaggedMetricRegistry metrics) {
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris(uri)
                        .security(SSL_CONFIG)
                        .build()))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .userAgent(USER_AGENT)
                .taggedMetricRegistry(metrics)
                .maxNumRetries(0)
                .backoffSlotSize(Duration.ZERO)
                .build();
    }
}
