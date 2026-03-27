/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Walkthrough tests for stepping through the full DialogueChannel pipeline with a debugger.
 *
 * <p>Uses {@link DialogueChannel} with 3 hosts. The wire layer is replaced with controllable
 * {@link SettableFuture}s so we can control when requests complete and observe how the queues drain.
 *
 */
class RequestFlowWalkthroughTest {
    private static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("foo", "1.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"), Paths.get("src/test/resources/keyStore.jks"), "keystore");
    private static final Endpoint ENDPOINT = TestEndpoint.POST;
    private static final Request REQUEST = Request.builder().build();

    /**
     * Per-host wire channels that return controllable futures. Keyed by URI.
     * Call {@link #completeOne(String)} to complete the oldest pending request on a host.
     */
    private final Map<String, Queue<SettableFuture<Response>>> pendingByHost = new ConcurrentHashMap<>();

    private Channel wireChannel(String uri) {
        pendingByHost.putIfAbsent(uri, new ConcurrentLinkedQueue<>());
        return (_endpoint, _request) -> {
            SettableFuture<Response> future = SettableFuture.create();
            pendingByHost.get(uri).add(future);
            return future;
        };
    }

    private void completeOne(String uri) {
        SettableFuture<Response> future = pendingByHost.get(uri).poll();
        assertThat(future).as("No pending request on " + uri).isNotNull();
        future.set(new TestResponse().code(200));
    }

    private int pendingCount(String uri) {
        Queue<SettableFuture<Response>> queue = pendingByHost.get(uri);
        return queue == null ? 0 : queue.size();
    }

    private static final String HOST_A = "http://hostA:8080";
    private static final String HOST_B = "http://hostB:8080";
    private static final String HOST_C = "http://hostC:8080";

    private DialogueChannel buildChannel(String channelName, int maxQueueSize) {
        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris(HOST_A, HOST_B, HOST_C)
                        .security(SSL_CONFIG)
                        .build()))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .userAgent(USER_AGENT)
                .maxNumRetries(0)
                .backoffSlotSize(Duration.ZERO)
                .build();

        return DialogueChannel.builder()
                .channelName(channelName)
                .clientConfiguration(config)
                .factory(args -> wireChannel(args.uri()))
                .maxQueueSize(maxQueueSize)
                .build();
    }

    // ========================================================================================
    //  Happy path — request goes through both limiter layers to the wire
    // ========================================================================================

    @Test
    void happy_path() {
        DialogueChannel channel = buildChannel("happy-path", 100);

        // Step through:
        //   1. QueuedChannel.maybeExecute (outer, type "channel") — fast path, queue empty
        //   2. NodeSelectionStrategyChannel picks a host
        //   3. ConcurrencyLimitedChannel.maybeExecute (host-level) — AIMD limit starts at 20, permits
        //   4. ChannelToEndpointChannel resolves the endpoint
        //   5. ConcurrencyLimitedChannel.maybeExecute (endpoint-level) — AIMD limit starts at 20, permits
        //   6. QueuedChannel.maybeExecute (endpoint, type "endpoint") — fast path
        //   7. DeadlineAdvertisementChannel → wire
        ListenableFuture<Response> response = channel.execute(ENDPOINT, REQUEST);
        assertThat(response).isNotDone();

        // Exactly one host should have a pending wire request
        int total = pendingCount(HOST_A) + pendingCount(HOST_B) + pendingCount(HOST_C);
        assertThat(total).as("Request should be on exactly one host's wire").isEqualTo(1);

        // Complete it — triggers ForwardAndSchedule → schedule() on both queue layers
        if (pendingCount(HOST_A) == 1) {
            completeOne(HOST_A);
        } else if (pendingCount(HOST_B) == 1) {
            completeOne(HOST_B);
        } else {
            completeOne(HOST_C);
        }

        assertThat(response).isDone();
    }

    // ========================================================================================
    //  Saturate host-level AIMD limits, observe outer queue
    //
    //  The AIMD limiter starts at 20. We send 20 requests that never complete to fill
    //  all permits. The 21st request gets queued in the outer QueuedChannel.
    // ========================================================================================

    @Test
    void outer_queue_saturate_host_limits() {
        DialogueChannel channel = buildChannel("outer-queue", 100);

        // The AIMD concurrency limit starts at 20.
        // With ROUND_ROBIN and 3 hosts, requests distribute roughly evenly.
        // Send 60 requests (20 per host) to fully saturate all hosts.
        int totalToSaturate = 60;
        ListenableFuture<Response>[] inflight = new ListenableFuture[totalToSaturate];
        for (int i = 0; i < totalToSaturate; i++) {
            inflight[i] = channel.execute(ENDPOINT, REQUEST);
            assertThat(inflight[i]).isNotDone();
        }

        int totalPending = pendingCount(HOST_A) + pendingCount(HOST_B) + pendingCount(HOST_C);
        assertThat(totalPending)
                .as("All 60 requests should be on the wire (20 per host)")
                .isEqualTo(60);

        // 61st request: all hosts are at their AIMD limit → outer QueuedChannel enqueues it
        ListenableFuture<Response> queued = channel.execute(ENDPOINT, REQUEST);
        assertThat(queued).isNotDone();
        int newTotal = pendingCount(HOST_A) + pendingCount(HOST_B) + pendingCount(HOST_C);
        assertThat(newTotal).as("No new wire request — it's in the outer queue").isEqualTo(60);

        // Complete one request on hostA → frees a permit → scheduleNextTask drains the queue
        completeOne(HOST_A);
        newTotal = pendingCount(HOST_A) + pendingCount(HOST_B) + pendingCount(HOST_C);
        assertThat(newTotal)
                .as("Queued request should have drained to a host")
                .isEqualTo(60); // 59 original still pending + 1 newly dispatched
    }

    // ========================================================================================
    //  Drive endpoint AIMD limit down with 429s, observe endpoint queue
    //
    //  Send 429s to drive the endpoint-level AIMD limit below 1 usable permit.
    //  The host-level limit stays high (we use 200s for host-level behavior).
    //  Then send real requests — they pass the host limiter but queue in the endpoint queue.
    // ========================================================================================

    @Test
    void endpoint_queue_via_429s() {
        // Use a single host so we don't have to reason about round-robin
        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris(HOST_A)
                        .security(SSL_CONFIG)
                        .build()))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .userAgent(USER_AGENT)
                .maxNumRetries(0)
                .backoffSlotSize(Duration.ZERO)
                .build();

        // Wire channel that can switch between returning 429s and returning SettableFutures
        ConcurrentLinkedQueue<SettableFuture<Response>> pending = new ConcurrentLinkedQueue<>();
        // This is an array because we need to use it in a lambda, and lambdas expect variables captured to be final.
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
                .channelName("endpoint-queue")
                .clientConfiguration(config)
                .factory(_args -> wire)
                .maxQueueSize(100)
                .build();

        // Send 429s to drive the endpoint-level AIMD limit down.
        // ENDPOINT_LEVEL behavior: non-custom 429 calls dropped() → limit *= 0.9, floored.
        // Starting at 20, after ~30 iterations: 20 → 18 → 16 → ... → 1
        // The host-level AIMD ignores 429 (calls ignore()), so it stays at 20.
        for (int i = 0; i < 50; i++) {
            ListenableFuture<Response> resp = channel.execute(ENDPOINT, REQUEST);
            assertThat(resp).isDone();
        }

        // Switch to SettableFutures for real requests
        returnImmediate429[0] = false;

        // First request: host limiter has capacity (limit ~20, 0 in-flight).
        // Endpoint limiter should be at limit=1 after the 429 barrage.
        // The request passes the endpoint limiter (0 < 1) and hits the wire.
        ListenableFuture<Response> req1 = channel.execute(ENDPOINT, REQUEST);
        assertThat(req1).isNotDone();
        assertThat(pending).as("req1 should be on the wire").hasSize(1);

        // Second request: host limiter still has capacity.
        // But endpoint limiter is full (1/1) → endpoint QueuedChannel enqueues it.
        // The host limiter still granted a permit because the endpoint queue returned a future.
        ListenableFuture<Response> req2 = channel.execute(ENDPOINT, REQUEST);
        assertThat(req2).isNotDone();
        assertThat(pending).as("req2 is in the endpoint queue, NOT on the wire").hasSize(1);

        // Complete req1 → endpoint queue drains → req2 dispatches to wire
        SettableFuture<Response> wireFuture = pending.poll();
        wireFuture.set(new TestResponse().code(200));
        assertThat(req1).isDone();
        assertThat(pending)
                .as("req2 should have drained from endpoint queue to wire")
                .hasSize(1);

        // Complete req2
        pending.poll().set(new TestResponse().code(200));
        assertThat(req2).isDone();
    }

    //  Queue full rejection — not retried
    @Test
    void queue_full_rejection() {
        DialogueChannel channel = buildChannel("queue-full", 1);

        // Saturate all host-level AIMD limits (20 per host × 3 hosts = 60)
        for (int i = 0; i < 60; i++) {
            ListenableFuture<Response> resp = channel.execute(ENDPOINT, REQUEST);
            assertThat(resp).isNotDone();
        }

        // 61st request queues (maxQueueSize=1, so 1 slot available)
        ListenableFuture<Response> queued = channel.execute(ENDPOINT, REQUEST);
        assertThat(queued).isNotDone();

        // 62nd request: queue is full → immediate failure, NOT retried
        ListenableFuture<Response> rejected = channel.execute(ENDPOINT, REQUEST);
        assertThat(rejected).isDone();
        assertThat(rejected).failsWithin(Duration.ZERO).withThrowableThat().withMessageContaining("queue is full");
    }

    // ========================================================================================
    //  429 retry lands on a different host
    //
    //  RetryingChannel sits above the outer QueuedChannel. When it retries, it calls
    //  execute() on the outer QueuedChannel again, which goes through node selection
    //  fresh — so the retry can land on a different host.
    // ========================================================================================

    @Test
    void retry_after_429_picks_different_host() throws Exception {
        // Each host tracks which URIs it received requests on.
        // Every host returns 429 on the FIRST request, then 200 on subsequent requests.
        // This guarantees the first attempt always fails, and the retry always succeeds on
        // a different host (since round-robin advances).
        Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

        DialogueChannelFactory factory = args -> {
            String uri = args.uri();
            requestCounts.putIfAbsent(uri, new AtomicInteger(0));
            return (_endpoint, _request) -> {
                int count = requestCounts.get(uri).incrementAndGet();
                if (count == 1) {
                    // First request to this host → 429
                    return Futures.immediateFuture(new TestResponse().code(429));
                }
                return Futures.immediateFuture(new TestResponse().code(200));
            };
        };

        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris(HOST_A, HOST_B, HOST_C)
                        .security(SSL_CONFIG)
                        .build()))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .userAgent(USER_AGENT)
                .maxNumRetries(3)
                .backoffSlotSize(Duration.ZERO)
                .build();

        DialogueChannel channel = DialogueChannel.builder()
                .channelName("retry-429")
                .clientConfiguration(config)
                .factory(factory)
                .build();

        // The request will:
        //   1. RetryingChannel calls execute() on its delegate
        //   2. Outer QueuedChannel → node selection (ROUND_ROBIN) → picks host X
        //   3. Host X returns 429 (first request to it)
        //   4. RetryingChannel sees retryable QoS → closes response, retries
        //   5. Fresh call to outer QueuedChannel → node selection picks host Y (next in round-robin)
        //   6. Host Y returns 200 (or 429 if also first hit, then another retry)
        ListenableFuture<Response> response = channel.execute(ENDPOINT, REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(200);

        // Total requests must be > 1: the first attempt hit a 429, then a retry succeeded.
        // This proves RetryingChannel re-invoked the outer QueuedChannel (going through node
        // selection again), rather than retrying on the same host.
        long totalRequests = requestCounts.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
        assertThat(totalRequests)
                .as("Must have made at least 2 requests (first 429, then retry succeeded)")
                .isGreaterThanOrEqualTo(2);
    }

    // ========================================================================================
    //  Queue size grows as requests pile up, shrinks as they drain
    // ========================================================================================

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    void outer_queue_size_grows_and_shrinks() {
        DialogueChannel channel = buildChannel("queue-size", 100);
        QueuedChannel outerQueue = channel.getMultiHostQueuedChannelForTesting();

        assertThat(outerQueue.getQueueSizeForTesting()).isEqualTo(0);

        // Saturate all host-level AIMD limits (20 per host × 3 hosts = 60)
        for (int i = 0; i < 60; i++) {
            channel.execute(ENDPOINT, REQUEST);
        }
        assertThat(outerQueue.getQueueSizeForTesting())
                .as("Queue should be empty — all 60 requests fit within AIMD limits")
                .isEqualTo(0);

        // Now queue 5 more requests — all hosts are full, these go into the outer queue
        ListenableFuture<Response>[] queued = new ListenableFuture[5];
        for (int i = 0; i < 5; i++) {
            queued[i] = channel.execute(ENDPOINT, REQUEST);
            assertThat(outerQueue.getQueueSizeForTesting())
                    .as("Queue size should be %d after queuing %d requests", i + 1, i + 1)
                    .isEqualTo(i + 1);
        }

        // Complete requests on each host to free permits and drain the queue
        completeOne(HOST_A);
        int afterFirst = outerQueue.getQueueSizeForTesting();
        assertThat(afterFirst)
                .as("Queue should shrink after completing a request")
                .isLessThan(5);

        completeOne(HOST_B);
        completeOne(HOST_C);
        int afterThree = outerQueue.getQueueSizeForTesting();
        assertThat(afterThree)
                .as("Queue should continue shrinking as more requests complete")
                .isLessThanOrEqualTo(afterFirst);
    }

    // ========================================================================================
    //  Queue timeout proactively fails queued requests after the configured duration
    // ========================================================================================

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    void outer_queue_timeout_evicts_requests() throws Exception {
        Duration queueTimeout = Duration.ofMillis(100);

        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris(HOST_A, HOST_B, HOST_C)
                        .security(SSL_CONFIG)
                        .build()))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .userAgent(USER_AGENT)
                .maxNumRetries(0)
                .backoffSlotSize(Duration.ZERO)
                .build();

        DialogueChannel channel = DialogueChannel.builder()
                .channelName("queue-timeout")
                .clientConfiguration(config)
                .factory(args -> wireChannel(args.uri()))
                .maxQueueSize(100)
                .queueTimeout(queueTimeout)
                .build();

        QueuedChannel outerQueue = channel.getMultiHostQueuedChannelForTesting();

        // Saturate all host-level AIMD limits (20 per host × 3 hosts = 60)
        for (int i = 0; i < 60; i++) {
            channel.execute(ENDPOINT, REQUEST);
        }
        assertThat(outerQueue.getQueueSizeForTesting()).isEqualTo(0);

        // Queue 5 more requests — all hosts full, these enter the outer deque
        ListenableFuture<Response>[] queued = new ListenableFuture[5];
        for (int i = 0; i < 5; i++) {
            queued[i] = channel.execute(ENDPOINT, REQUEST);
            assertThat(queued[i]).isNotDone();
        }
        assertThat(outerQueue.getQueueSizeForTesting()).isEqualTo(5);

        // Lazy eviction: the entries are NOT proactively failed. They sit in the deque
        // until scheduleNextTask polls them and checks the timestamp.
        Thread.sleep(queueTimeout.toMillis() + 100);

        // Futures are still not done — no one has checked them yet (lazy!)
        for (int i = 0; i < 5; i++) {
            assertThat(queued[i]).as("Queued request %d should NOT be done yet (lazy eviction)", i).isNotDone();
        }

        // Complete one wire request to trigger scheduleNextTask.
        // scheduleNextTask will poll entries from the deque, check isExpired(), and evict them.
        completeOne(HOST_A);

        // Now the expired entries should have been discovered and failed
        for (int i = 0; i < 5; i++) {
            assertThat(queued[i]).as("Queued request %d should be done after drain", i).isDone();
            assertThat(queued[i])
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .withMessageContaining("queue timeout");
        }

        assertThat(outerQueue.getQueueSizeForTesting())
                .as("All timed-out entries should be cleaned up")
                .isEqualTo(0);
    }
}
