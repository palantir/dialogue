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
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines;
import com.palantir.deadlines.Deadlines.Enforcement;
import com.palantir.deadlines.Deadlines.RequestDecodingAdapter;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.tracing.CloseableTracer;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates that requests with expired deadlines queued in the endpoint queue hold host-level
 * concurrency permits, starving other endpoints on the same host.
 */
class DeadlineEarlyEvictionTest {
    private static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("test", "1.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"), Paths.get("src/test/resources/keyStore.jks"), "keystore");
    private static final Request REQUEST = Request.builder().build();

    private final Queue<SettableFuture<Response>> wireRequests = new ConcurrentLinkedQueue<>();
    private boolean return429 = true;

    private DialogueChannel createChannel() {
        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris("http://host:8080")
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
                .factory(_args -> (_endpoint, _request) -> {
                    if (return429) {
                        return Futures.immediateFuture(new TestResponse().code(429));
                    }
                    SettableFuture<Response> future = SettableFuture.create();
                    wireRequests.add(future);
                    return future;
                })
                .deadlineEnforcement(Optional.of(true))
                .build();
    }

    /** Drive the endpoint-level AIMD limit to 1 via 429s. Host-level limit stays at 20. */
    @SuppressWarnings("FutureReturnValueIgnored")
    private void driveEndpointLimitDown(DialogueChannel channel) {
        for (int i = 0; i < 50; i++) {
            channel.execute(TestEndpoint.POST, REQUEST);
        }
        return429 = false;
    }

    /** Set a deadline in the current trace span. */
    private static void setDeadline(String seconds) {
        Request inbound =
                Request.builder().putHeaderParams("Expect-Within", seconds).build();
        Deadlines.parseFromRequest(Optional.empty(), inbound, Decoder.INSTANCE, Enforcement.ENFORCE);
    }

    /**
     * Expired-deadline requests queued in the endpoint queue hold host permits, blocking other endpoints.
     *
     * <p>Setup: single host, POST endpoint AIMD limit driven to 1, host limit at 20.
     * <ol>
     *     <li>Send 1 POST request to wire + 19 POST requests with a 500ms deadline into the endpoint queue
     *         (each holding a host permit, 20/20 consumed)</li>
     *     <li>Send a GET request (no deadline) — blocked in outer queue, no host permits available</li>
     *     <li>Wait for deadline to expire — GET is still blocked by dead POST requests</li>
     *     <li>Complete the wire request — endpoint queue drains, dead requests fail, host permits freed,
     *         GET proceeds</li>
     * </ol>
     */
    @Test
    void expired_deadline_requests_in_endpoint_queue_hold_host_permits() throws Exception {
        DialogueChannel channel = createChannel();
        driveEndpointLimitDown(channel);

        // Submit POST requests with a short deadline. Each queued request holds a host permit.
        ListenableFuture<Response> blockingRequest;
        ListenableFuture<Response>[] queuedPosts = new ListenableFuture[19];
        try (CloseableTracer tracer = CloseableTracer.startSpan("deadline-posts")) {
            setDeadline("0.5");
            blockingRequest = channel.execute(TestEndpoint.POST, REQUEST);
            assertThat(wireRequests).as("first request on wire").hasSize(1);

            for (int i = 0; i < 19; i++) {
                queuedPosts[i] = channel.execute(TestEndpoint.POST, REQUEST);
            }
            assertThat(wireRequests).as("19 queued in endpoint queue, not on wire").hasSize(1);
        }

        // GET request outside deadline context — blocked because all 20 host permits are held
        ListenableFuture<Response> getRequest = channel.execute(TestEndpoint.GET, REQUEST);
        assertThat(getRequest).isNotDone();
        assertThat(wireRequests).as("GET not on wire — no host permits").hasSize(1);

        // Deadline expires, but dead requests are still holding host permits
        Thread.sleep(600);
        assertThat(getRequest).as("GET still blocked by dead POST requests").isNotDone();

        // Complete the wire request — drains endpoint queue, dead requests fail, frees host permits
        wireRequests.poll().set(new TestResponse().code(200));
        assertThat(blockingRequest).isDone();

        for (int i = 0; i < 19; i++) {
            assertThat(queuedPosts[i])
                    .as("queued POST %d", i)
                    .failsWithin(Duration.ZERO)
                    .withThrowableThat()
                    .withCauseInstanceOf(DeadlineExpiredException.class);
        }

        // GET request should now be dispatched
        assertThat(wireRequests).as("GET now on wire").hasSize(1);
        wireRequests.poll().set(new TestResponse().code(200));
        assertThat(getRequest).isDone();
        assertThat(getRequest.get().code()).isEqualTo(200);
    }

    private enum Decoder implements RequestDecodingAdapter<Request> {
        INSTANCE;

        @Override
        public Optional<String> getFirstHeader(Request request, String headerName) {
            return request.headerParams().get(headerName).stream().findFirst();
        }
    }
}
