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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConstructUsing;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Factory;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

// has to live in a separate project to avoid IntelliJ complaining about a cycle
class DialogueTest {
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("../dialogue-client-test-lib/src/main/resources/trustStore.jks"),
            Paths.get("../dialogue-client-test-lib/src/main/resources/keyStore.jks"),
            "keystore");

    private static final ClientConfiguration LEGACY = createTestConfig("https://foo", "https://bar");
    private static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("foo", "1.0.0"));
    private static final ListenableValue<ClientConfig> listenableConfig = new ListenableValue<>(ClientConfig.builder()
            .from(LEGACY)
            .httpClientType(ClientConfig.HttpClientType.APACHE)
            .userAgent(USER_AGENT)
            .build());

    @Test
    void can_create_a_raw_apache_channel() throws Exception {
        try (ClientPool clientPool = Dialogue.newClientPool()) {

            Channel channel = clientPool.rawHttpChannel("https://foo", listenableConfig);
            assertThat(channel).isNotNull();

            ListenableFuture<Response> response =
                    channel.execute(FakeEndpoint.INSTANCE, Request.builder().build());
            assertThatThrownBy(() -> Futures.getUnchecked(response))
                    .hasMessageContaining("foo: nodename nor servname provided, or not known");
        }
    }

    @Test
    void dialogue_can_reflectively_instantiate_stuff() {
        Channel channel = mock(Channel.class);
        BlockingFooService instance = ClientPoolImpl.instantiateDialogueInterface(BlockingFooService.class, channel);
        assertThat(instance).isInstanceOf(BlockingFooService.class);

        assertThat(instance.doSomething()).isEqualTo("Hello");
    }

    private static ClientConfiguration createTestConfig(String... uri) {
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(
                        ImmutableList.copyOf(uri),
                        SslSocketFactories.createSslSocketFactory(SSL_CONFIG),
                        SslSocketFactories.createX509TrustManager(SSL_CONFIG)))
                .maxNumRetries(0)
                .build();
    }

    @ConstructUsing(BlockingFooService.MyFactory.class)
    private interface BlockingFooService {

        String doSomething();

        class MyFactory implements Factory<BlockingFooService> {
            @Override
            public BlockingFooService construct(Channel _channel) {
                return new BlockingFooService() {
                    @Override
                    public String doSomething() {
                        return "Hello";
                    }
                };
            }
        }
    }

    private enum FakeEndpoint implements Endpoint {
        INSTANCE;

        @Override
        public void renderPath(Map<String, String> _params, UrlBuilder url) {
            url.pathSegment("/string");
        }

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.POST;
        }

        @Override
        public String serviceName() {
            return "service";
        }

        @Override
        public String endpointName() {
            return "endpoint";
        }

        @Override
        public String version() {
            return "1.0.0";
        }
    }
}
