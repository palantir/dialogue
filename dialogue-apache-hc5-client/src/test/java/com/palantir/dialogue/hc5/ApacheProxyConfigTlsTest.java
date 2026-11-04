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
package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.AbstractProxyConfigTlsTest;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.core.DialogueChannel;
import io.undertow.server.handlers.ConnectHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public final class ApacheProxyConfigTlsTest extends AbstractProxyConfigTlsTest {
    private static final Request AUTHENTICATED_REQUEST = Request.builder()
            .putHeaderParams("Authorization", "Bearer origin-token")
            .build();

    @Override
    protected Channel create(ClientConfiguration config) {
        return ApacheHttpClientChannels.create(config);
    }

    @Test
    void refreshes_proxy_authorization_on_reconnect() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>("Bearer first-token");
        List<String> credentials = new CopyOnWriteArrayList<>();
        recordConnectAuthorization(credentials);
        setServerHandler(exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION))
                    .isEqualTo("Bearer origin-token");
            assertThat(exchange.getRequestHeaders().getFirst(Headers.PROXY_AUTHORIZATION))
                    .isNull();
            exchange.getResponseHeaders().put(Headers.CONNECTION, "close");
            exchange.getResponseSender().send("origin");
        });
        ClientConfiguration config = proxiedConfig();
        try (ApacheHttpClientChannels.CloseableClient client = ApacheHttpClientChannels.clientBuilder()
                .clientConfiguration(config)
                .clientName("refreshing-proxy-authorization")
                .proxyAuthorization(authorization::get)
                .build()) {
            Channel channel =
                    ApacheHttpClientChannels.createSingleUri(config.uris().get(0), client);
            for (String token : List.of("Bearer first-token", "Bearer second-token")) {
                authorization.set(token);
                try (Response response =
                        channel.execute(TestEndpoint.GET, AUTHENTICATED_REQUEST).get(10, TimeUnit.SECONDS)) {
                    assertThat(response.code()).isEqualTo(200);
                    assertThat(response.body()).hasContent("origin");
                }
            }
        }
        assertThat(credentials).containsExactly("Bearer first-token", "Bearer second-token");
    }

    @Test
    void refreshes_proxy_authorization_on_retry() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>("Bearer first-token");
        List<String> credentials = new CopyOnWriteArrayList<>();
        recordConnectAuthorization(credentials);
        AtomicInteger originRequests = new AtomicInteger();
        setServerHandler(exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION))
                    .isEqualTo("Bearer origin-token");
            assertThat(exchange.getRequestHeaders().getFirst(Headers.PROXY_AUTHORIZATION))
                    .isNull();
            exchange.getResponseHeaders().put(Headers.CONNECTION, "close");
            if (originRequests.getAndIncrement() == 0) {
                authorization.set("Bearer retry-token");
                exchange.setStatusCode(503);
            }
            exchange.getResponseSender().send("origin");
        });
        ClientConfiguration config = ClientConfiguration.builder()
                .from(proxiedConfig())
                .maxNumRetries(1)
                .build();
        try (ApacheHttpClientChannels.CloseableClient client = ApacheHttpClientChannels.clientBuilder()
                .clientConfiguration(config)
                .clientName("retrying-proxy-authorization")
                .proxyAuthorization(authorization::get)
                .build()) {
            Channel channel = DialogueChannel.builder()
                    .clientConfiguration(config)
                    .channelName("retrying-proxy-authorization")
                    .factory(args -> ApacheHttpClientChannels.createSingleUri(args, client))
                    .build();
            try (Response response =
                    channel.execute(TestEndpoint.GET, AUTHENTICATED_REQUEST).get(10, TimeUnit.SECONDS)) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body()).hasContent("origin");
            }
        }
        assertThat(credentials).containsExactly("Bearer first-token", "Bearer retry-token");
        assertThat(originRequests).hasValue(2);
    }

    @Test
    void does_not_apply_proxy_authorization_to_direct_requests() throws Exception {
        setServerHandler(exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst(Headers.PROXY_AUTHORIZATION))
                    .isNull();
            exchange.getResponseSender().send("origin");
        });
        ClientConfiguration config = TestConfigurations.create(serverUri());
        try (ApacheHttpClientChannels.CloseableClient client = ApacheHttpClientChannels.clientBuilder()
                        .clientConfiguration(config)
                        .clientName("direct-proxy-authorization")
                        .proxyAuthorization(() -> {
                            throw new IllegalStateException("Proxy authorization must not be requested");
                        })
                        .build();
                Response response = ApacheHttpClientChannels.createSingleUri(
                                config.uris().get(0), client)
                        .execute(TestEndpoint.GET, AUTHENTICATED_REQUEST)
                        .get(10, TimeUnit.SECONDS)) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).hasContent("origin");
        }
    }

    @Test
    void fails_closed_when_proxy_authorization_cannot_be_resolved() throws Exception {
        List<String> credentials = new CopyOnWriteArrayList<>();
        recordConnectAuthorization(credentials);
        AtomicInteger originRequests = new AtomicInteger();
        setServerHandler(exchange -> {
            originRequests.incrementAndGet();
            exchange.getResponseSender().send("origin");
        });
        IllegalStateException failure = new IllegalStateException("Token resolution failed");
        ClientConfiguration config = proxiedConfig();
        try (ApacheHttpClientChannels.CloseableClient client = ApacheHttpClientChannels.clientBuilder()
                .clientConfiguration(config)
                .clientName("unresolved-proxy-authorization")
                .proxyAuthorization(() -> {
                    throw failure;
                })
                .build()) {
            Channel channel =
                    ApacheHttpClientChannels.createSingleUri(config.uris().get(0), client);
            assertThatThrownBy(() -> channel.execute(TestEndpoint.GET, AUTHENTICATED_REQUEST)
                            .get(10, TimeUnit.SECONDS))
                    .hasCause(failure);
        }
        assertThat(credentials).isEmpty();
        assertThat(originRequests).hasValue(0);
    }

    @Test
    void rejects_basic_credentials_with_proxy_authorization_supplier() {
        ClientConfiguration config = ClientConfiguration.builder()
                .from(proxiedConfig())
                .proxyCredentials(BasicCredentials.of("user", "password"))
                .build();
        assertThatThrownBy(() -> ApacheHttpClientChannels.clientBuilder()
                        .clientConfiguration(config)
                        .clientName("conflicting-proxy-authorization")
                        .proxyAuthorization(() -> "Bearer proxy-token")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proxyAuthorization and proxyCredentials cannot both be configured");
    }

    private void recordConnectAuthorization(List<String> credentials) {
        setProxyHandler(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo(Methods.CONNECT);
            assertThat(exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION))
                    .isNull();
            credentials.add(exchange.getRequestHeaders().getFirst(Headers.PROXY_AUTHORIZATION));
            new ConnectHandler(ResponseCodeHandler.HANDLE_500).handleRequest(exchange);
        });
    }

    private ClientConfiguration proxiedConfig() {
        return ClientConfiguration.builder()
                .from(TestConfigurations.create(serverUri()))
                .proxy(httpsProxySelector())
                .build();
    }
}
