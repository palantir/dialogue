/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.tracing.TestTracing;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class ChannelsTest {

    public static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("foo", "1.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"), Paths.get("src/test/resources/keyStore.jks"), "keystore");
    private static final ClientConfiguration stubConfig = ClientConfiguration.builder()
            .from(ClientConfigurations.of(ServiceConfiguration.builder()
                    .addUris("http://localhost")
                    .security(SSL_CONFIG)
                    .build()))
            .userAgent(USER_AGENT)
            .build();

    @Mock
    private Channel channel1;

    @Mock
    private Channel channel2;

    private Endpoint endpoint = new Endpoint() {
        @Override
        public void renderPath(Map<String, String> _params, UrlBuilder _url) {}

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String serviceName() {
            return "test-service";
        }

        @Override
        public String endpointName() {
            return "test-endpoint";
        }

        @Override
        public String version() {
            return "1.0.0";
        }
    };

    @Mock
    private Response response;

    private Request request = Request.builder().build();
    private Channel channel;

    @BeforeEach
    public void before() {
        channel = Channels.create(ImmutableList.of(channel1), stubConfig);

        ListenableFuture<Response> expectedResponse = Futures.immediateFuture(response);
        lenient().when(channel1.execute(eq(endpoint), any())).thenReturn(expectedResponse);
    }

    @Test
    public void testRequestMakesItThrough() throws ExecutionException, InterruptedException {
        assertThat(channel.execute(endpoint, request).get()).isEqualTo(response);
    }

    @Test
    public void bad_channel_throwing_an_exception_still_returns_a_future() {
        Channel badUserImplementation = new Channel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
                throw new IllegalStateException("Always throw");
            }
        };

        channel = Channels.create(ImmutableList.of(badUserImplementation), stubConfig);

        // this should never throw
        ListenableFuture<Response> future = channel.execute(endpoint, request);

        // only when we access things do we allow exceptions
        assertThatThrownBy(() -> Futures.getUnchecked(future)).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    public void bad_channel_throwing_an_error_still_returns_a_future() {
        Channel badUserImplementation = new Channel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
                throw new NoClassDefFoundError("something is broken");
            }
        };

        channel = Channels.create(ImmutableList.of(badUserImplementation), stubConfig);

        // this should never throw
        ListenableFuture<Response> future = channel.execute(endpoint, request);

        // only when we access things do we allow exceptions
        assertThatThrownBy(() -> Futures.getUnchecked(future)).hasCauseInstanceOf(NoClassDefFoundError.class);
    }

    @Test
    // @Disabled("equals methods don't work well yet")
    void test_my_live_reloading_thingy() throws Exception {
        lenient()
                .when(channel2.execute(any(), any()))
                .thenReturn(Futures.immediateFailedFuture(new RuntimeException("borf")));

        DialogueChannel chan = DialogueChannel.builder()
                .channels(ImmutableList.of(channel1, channel1)) // >1 channels ensures we get PinUntilError
                .clientConfiguration(stubConfig)
                .build();

        ListenableFuture<Response> response1 = chan.execute(endpoint, request);
        assertThat(response1.get()).isSameAs(response);

        chan = DialogueChannel.builder()
                .from(chan)
                .channels(ImmutableList.of(channel2, channel2, channel2, channel2, channel1, channel2, channel2))
                .clientConfiguration(stubConfig)
                .build();

        ListenableFuture<Response> response2 = chan.execute(endpoint, request);
        assertThat(response2.get())
                .describedAs("We should still be locked on to the original channel1")
                .isSameAs(response);
        verifyNoInteractions(channel2);
    }

    @Test
    @TestTracing(snapshot = true)
    public void traces_on_retries() throws Exception {
        when(response.code()).thenReturn(429);
        try (Response response = channel.execute(endpoint, request).get()) {
            assertThat(response.code()).isEqualTo(429);
        }
    }

    @Test
    @TestTracing(snapshot = true)
    public void traces_on_success() throws Exception {
        when(response.code()).thenReturn(200);
        try (Response response = channel.execute(endpoint, request).get()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }
}
