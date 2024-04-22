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

import com.google.common.collect.ImmutableSet;
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
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class NoEndpointLimitTest {

    public static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("foo", "1.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"), Paths.get("src/test/resources/keyStore.jks"), "keystore");
    private static final ClientConfiguration stubConfig = ClientConfiguration.builder()
            .from(ClientConfigurations.of(ServiceConfiguration.builder()
                    .addUris("http://localhost")
                    .security(SSL_CONFIG)
                    .build()))
            .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
            .userAgent(USER_AGENT)
            .backoffSlotSize(Duration.ZERO)
            .build();

    private final Endpoint defaultEndpoint = TestEndpoint.POST;
    private final Endpoint noEndpointQueueEndpoint = new Endpoint() {
        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.POST;
        }

        @Override
        public String serviceName() {
            return "noEndpointQueue";
        }

        @Override
        public String endpointName() {
            return "noEndpointQueue";
        }

        @Override
        public String version() {
            return "0.0.0";
        }

        @Override
        public Set<String> tags() {
            return ImmutableSet.of("dialogue-no-endpoint-limit");
        }
    };

    @Test
    public void test_endpoint_concurrency_limit_opt_out() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        SettableFuture<Response> responseFuture = SettableFuture.create();
        Channel delegate = (_endpoint, request) -> {
            // Respond 429 immediately for 'warmup' requests to drop the per-endpoint concurrency limit
            if (request.queryParams().containsKey("warmup")) {
                return Futures.immediateFuture(new TestResponse().code(429));
            }
            inFlight.incrementAndGet();
            return responseFuture;
        };

        DialogueChannel channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> delegate)
                .build();

        for (int i = 0; i < 100; i++) {
            Request request = Request.builder().putQueryParams("warmup", "true").build();
            ListenableFuture<Response> defaultFuture = channel.execute(defaultEndpoint, request);
            assertThat(defaultFuture).succeedsWithin(Duration.ofSeconds(1));
            assertThat(defaultFuture.get()).extracting(Response::code).isEqualTo(429);
            ListenableFuture<Response> noEndpointQueueFuture = channel.execute(noEndpointQueueEndpoint, request);
            assertThat(noEndpointQueueFuture).succeedsWithin(Duration.ofSeconds(1));
            assertThat(noEndpointQueueFuture.get()).extracting(Response::code).isEqualTo(429);
        }

        Request request = Request.builder().build();
        assertThat(inFlight).hasValue(0);
        ListenableFuture<Response> defaultFirst = channel.execute(defaultEndpoint, request);
        assertThat(inFlight).hasValue(1);
        ListenableFuture<Response> defaultSecond = channel.execute(defaultEndpoint, request);
        assertThat(inFlight)
                .as("per-endpoint queue should prevent a second request from firing")
                .hasValue(1);
        ListenableFuture<Response> noEndpointQueueFirst = channel.execute(noEndpointQueueEndpoint, request);
        assertThat(inFlight).hasValue(2);
        ListenableFuture<Response> noEndpointQueueSecond = channel.execute(noEndpointQueueEndpoint, request);
        assertThat(inFlight)
                .as("per-endpoint queue should not be enabled, so this request should be sent immediately")
                .hasValue(3);

        assertThat(defaultFirst).isNotDone();
        assertThat(defaultSecond).isNotDone();
        assertThat(noEndpointQueueFirst).isNotDone();
        assertThat(noEndpointQueueSecond).isNotDone();
    }
}
