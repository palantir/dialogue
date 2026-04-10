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
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    private static final Duration QUEUE_TIMEOUT = Duration.ofSeconds(5);

    @Nested
    class ChannelQueueTimeout {

        @Test
        @SuppressWarnings("FutureReturnValueIgnored")
        void queued_requests_are_failed_after_timeout() throws Exception {
            WireChannel wire = new WireChannel();
            DialogueChannel channel = buildChannel(wire, HOST_A, HOST_B, HOST_C);
            QueuedChannel outerQueue = channel.getMultiHostQueuedChannelForTesting();

            // Saturate all host-level AIMD limits (20 per host × 3 hosts = 60)
            for (int i = 0; i < 60; i++) {
                channel.execute(ENDPOINT, Request.builder().build());
            }
            assertThat(outerQueue.getQueueSizeForTesting()).isEqualTo(0);

            // Queue 5 more — all hosts full
            ListenableFuture<Response>[] queued = new ListenableFuture[5];
            for (int i = 0; i < 5; i++) {
                queued[i] = channel.execute(ENDPOINT, Request.builder().build());
                assertThat(queued[i]).isNotDone();
            }
            assertThat(outerQueue.getQueueSizeForTesting()).isEqualTo(5);

            // Wait for timeout
            Thread.sleep(QUEUE_TIMEOUT.toMillis() + 100);

            for (int i = 0; i < 5; i++) {
                assertThat(queued[i]).isDone();
                assertThat(queued[i])
                        .failsWithin(Duration.ZERO)
                        .withThrowableThat()
                        .withMessageContaining("queue timeout");
            }

            // Trigger scheduleNextTask to clean up queue entries
            wire.completeOldest();
            // The queue should now be empty because scheduleNextTask will clean up the queue entries that have timed
            // out
            assertThat(outerQueue.getQueueSizeForTesting()).isEqualTo(0);
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

            // Wait past the original timeout
            Thread.sleep(QUEUE_TIMEOUT.toMillis() + 100);

            // The dispatched request should NOT have timed out — timeout was cancelled on dispatch
            assertThat(queued)
                    .as("Timeout should have been cancelled on dispatch")
                    .isNotDone();
        }
    }

    @Nested
    class EndpointQueueTimeout {

        @Test
        void request_times_out_in_endpoint_queue() throws Exception {
            // Single host, drive endpoint AIMD limit down with 429s
            ConcurrentLinkedQueue<SettableFuture<Response>> pending = new ConcurrentLinkedQueue<>();
            boolean[] returnImmediate429 = {true};
            Channel wire = (_endpoint, _request) -> {
                if (returnImmediate429[0]) {
                    return Futures.immediateFuture(new TestResponse().code(429));
                }
                SettableFuture<Response> future = SettableFuture.create();
                pending.add(future);
                return future;
            };

            DialogueChannel channel = DialogueChannel.builder()
                    .channelName("endpoint-timeout")
                    .clientConfiguration(singleHostConfig(HOST_A))
                    .factory(_args -> wire)
                    .maxQueueSize(100)
                    .queueTimeout(QUEUE_TIMEOUT)
                    .build();

            // Drive endpoint AIMD limit down to 1
            for (int i = 0; i < 50; i++) {
                ListenableFuture<Response> resp =
                        channel.execute(ENDPOINT, Request.builder().build());
                assertThat(resp).isDone();
            }

            returnImmediate429[0] = false;

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

            // Wait for timeout, request2 should be failed
            Thread.sleep(QUEUE_TIMEOUT.toMillis() + 100);
            assertThat(request2).isDone();
            assertThat(request2).failsWithin(Duration.ZERO).withThrowableThat().withMessageContaining("queue timeout");

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
        void timed_out_request_is_not_retried() throws Exception {
            // Track how many times the wire is hit
            java.util.concurrent.atomic.AtomicInteger wireHitCount = new java.util.concurrent.atomic.AtomicInteger();
            WireChannel wire = new WireChannel() {
                @Override
                public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                    wireHitCount.incrementAndGet();
                    return super.execute(endpoint, request);
                }
            };

            // Build with retries enabled
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

            // Saturate the host-level limit
            for (int i = 0; i < 20; i++) {
                channel.execute(ENDPOINT, Request.builder().build());
            }
            int wireHitsAfterSaturation = wireHitCount.get();

            // Queue a request — it will time out
            ListenableFuture<Response> queued =
                    channel.execute(ENDPOINT, Request.builder().build());

            // Wait for timeout
            Thread.sleep(QUEUE_TIMEOUT.toMillis() + 100);

            assertThat(queued).isDone();
            assertThat(queued).failsWithin(Duration.ZERO).withThrowableThat().withMessageContaining("queue timeout");

            // The wire should NOT have been hit again — the timeout exception is
            // SafeRuntimeException (not IOException), so RetryingChannel doesn't retry
            assertThat(wireHitCount.get())
                    .as("Wire should not be hit after timeout — timeout is not retried")
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

    private static DialogueChannel buildChannel(WireChannel wire, String... uris) {
        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .uris(java.util.List.of(uris))
                        .security(SSL_CONFIG)
                        .build()))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .userAgent(USER_AGENT)
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
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris(uri)
                        .security(SSL_CONFIG)
                        .build()))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .userAgent(USER_AGENT)
                .maxNumRetries(0)
                .backoffSlotSize(Duration.ZERO)
                .build();
    }
}
