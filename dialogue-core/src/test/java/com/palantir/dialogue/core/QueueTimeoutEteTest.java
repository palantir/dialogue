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

    @Nested
    class ChannelQueueTimeout {

        @Test
        @SuppressWarnings("FutureReturnValueIgnored")
        void queued_requests_are_failed_after_timeout() {
            WireChannel wire = new WireChannel();
            DefaultTaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
            DialogueChannel channel = buildChannel(wire, metrics, HOST_A, HOST_B, HOST_C);
            Counter queueSize = DialogueClientMetrics.of(metrics).requestsQueued("test");

            // Saturate all host-level AIMD limits (20 per host × 3 hosts = 60)
            for (int i = 0; i < 60; i++) {
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

            // Each queued request is failed once its timeout elapses.
            Awaitility.waitAtMost(QUEUE_TIMEOUT.multipliedBy(2)).untilAsserted(() -> {
                for (ListenableFuture<Response> future : queued) {
                    assertThat(future).isDone();
                }
            });
            for (ListenableFuture<Response> future : queued) {
                assertThat(future)
                        .failsWithin(Duration.ZERO)
                        .withThrowableThat()
                        .withMessageContaining("queue timeout");
            }

            // The queue size is decremented proactively when each timeout fires, without waiting for the next
            // scheduleNextTask drain to pop the timed-out entries.
            Awaitility.waitAtMost(QUEUE_TIMEOUT.multipliedBy(2))
                    .untilAsserted(() -> assertThat(queueSize.getCount()).isEqualTo(0));
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

            // Saturate the host-level limit (AIMD starts at 20)
            for (int i = 0; i < 20; i++) {
                channel.execute(ENDPOINT, Request.builder().build());
            }
            assertThat(wire.pending()).hasSize(20);

            // 21st request enters the channel queue
            ListenableFuture<Response> queued =
                    channel.execute(ENDPOINT, Request.builder().build());
            assertThat(queued).isNotDone();
            assertThat(wire.pending()).as("Queued, not on wire").hasSize(20);

            // Complete one -> frees a permit -> queued request is dispatched (timeout cancelled)
            wire.completeOldest();
            assertThat(wire.pending())
                    .as("Queued request should have been dispatched")
                    .hasSize(20);

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

            // Drive the endpoint AIMD limit down to its floor of 1. Each non-custom 429 multiplies the limit by 0.9,
            // so dropping from the initial 20 to 1 takes ~28 steps; 50 is comfortably more than enough.
            for (int i = 0; i < 50; i++) {
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

            // Wait for timeout, request2 should be failed
            Awaitility.waitAtMost(QUEUE_TIMEOUT.multipliedBy(2))
                    .untilAsserted(() -> assertThat(request2).isDone());
            assertThat(request2).failsWithin(Duration.ZERO).withThrowableThat().withMessageContaining("queue timeout");

            // The endpoint queue size is decremented proactively when the timeout fires.
            Awaitility.waitAtMost(QUEUE_TIMEOUT.multipliedBy(2))
                    .untilAsserted(() -> assertThat(endpointQueued.getCount()).isEqualTo(0));

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
                public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                    wireHitCount.incrementAndGet();
                    return super.execute(endpoint, request);
                }
            };

            // Build with retries enabled (and no backoff, so a retry would re-dispatch immediately)
            ClientConfiguration config = ClientConfiguration.builder()
                    .from(ClientConfigurations.of(ServiceConfiguration.builder()
                            .addUris(HOST_A)
                            .security(SSL_CONFIG)
                            .build()))
                    .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                    .userAgent(USER_AGENT)
                    .maxNumRetries(3)
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
            for (int i = 0; i < 20; i++) {
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
            assertThat(queued).failsWithin(Duration.ZERO).withThrowableThat().withMessageContaining("queue timeout");

            // Free every permit by completing the in-flight requests. Checking wireHitCount alone is not enough:
            // while all hosts are saturated a retried request would simply re-queue (never reaching the wire), so
            // the count would be unchanged whether or not a retry happened. Freeing capacity forces the question —
            // a retried attempt would now be dispatched to the wire, whereas a non-retried timeout leaves it
            // untouched.
            for (int i = 0; i < 20; i++) {
                wire.completeOldest();
            }
            assertThat(wireHitCount.get())
                    .as("A queue timeout must not be retried, so freeing capacity yields no new wire hit")
                    .isEqualTo(wireHitsAfterSaturation);
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
