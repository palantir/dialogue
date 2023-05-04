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

package com.palantir.dialogue;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.ConnectHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractProxyConfigTlsTest {

    private static final String REQUEST_BODY = "Hello, World";
    private static final Request request = Request.builder()
            .body(new RequestBody() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    output.write(REQUEST_BODY.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String contentType() {
                    return "text/plain";
                }

                @Override
                public boolean repeatable() {
                    return true;
                }

                @Override
                public void close() {}
            })
            .build();

    private int serverPort;
    private Undertow server;
    private volatile HttpHandler serverHandler;
    private volatile HttpHandler proxyHandler;

    private int proxyPort;
    private Undertow proxyServer;

    protected abstract Channel create(ClientConfiguration config);

    @BeforeEach
    public void beforeEach() {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        server = Undertow.builder()
                .setHandler(exchange -> serverHandler.handleRequest(exchange))
                .addHttpsListener(0, null, sslContext)
                .build();
        server.start();
        serverPort = getPort(server);
        proxyServer = Undertow.builder()
                .setHandler(exchange -> proxyHandler.handleRequest(exchange))
                .addHttpListener(0, null)
                .build();
        proxyServer.start();
        proxyPort = getPort(proxyServer);
    }

    private static int getPort(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }

    @AfterEach
    public void afterEach() {
        try {
            server.stop();
        } finally {
            proxyServer.stop();
        }
    }

    @Test
    public void testDirectVersusProxyReadTimeout() throws Exception {
        serverHandler = new BlockingHandler(exchange -> {
            Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(2));
            exchange.getResponseSender().send("server");
        });
        proxyHandler = new ConnectHandler(ResponseCodeHandler.HANDLE_500);

        ClientConfiguration directConfig = ClientConfiguration.builder()
                .from(TestConfigurations.create("https://localhost:" + serverPort))
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                .build();
        Channel directChannel = create(directConfig);
        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(directConfig)
                .proxy(createProxySelector("localhost", proxyPort))
                .build();
        Channel proxiedChannel = create(proxiedConfig);

        try (Response response =
                directChannel.execute(TestEndpoint.POST, request).get()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).hasContent("server");
        }
        try (Response response =
                proxiedChannel.execute(TestEndpoint.POST, request).get()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).hasContent("server");
        }
    }

    @Test
    public void testBasicAuthenticatedProxy() throws Exception {
        AtomicInteger requestIndex = new AtomicInteger();
        proxyHandler = exchange -> {
            HeaderMap requestHeaders = exchange.getRequestHeaders();
            HeaderMap responseHeaders = exchange.getResponseHeaders();

            switch (requestIndex.getAndIncrement()) {
                case 0:
                    // okhttp and hc5 differ in this case, okhttp always sends credentials over the wire
                    // while hc5 does not expose credentials until the server provides a challenge.
                    // assertThat(requestHeaders.getHeaderNames()).doesNotContain(Headers.PROXY_AUTHORIZATION);
                    responseHeaders.put(Headers.PROXY_AUTHENTICATE, "Basic realm=test");
                    exchange.setStatusCode(407); // indicates authenticated proxy
                    return;
                case 1:
                    assertThat(requestHeaders.get(Headers.PROXY_AUTHORIZATION))
                            .containsExactly("Basic ZmFrZVVzZXJAZmFrZS5jb206ZmFrZTpQYXNzd29yZA==");
                    new ConnectHandler(ResponseCodeHandler.HANDLE_500).handleRequest(exchange);
                    return;
            }
            throw new IllegalStateException("Expected exactly two requests");
        };
        serverHandler = new BlockingHandler(exchange -> {
            String requestBody = new String(ByteStreams.toByteArray(exchange.getInputStream()), StandardCharsets.UTF_8);
            assertThat(requestBody).isEqualTo(REQUEST_BODY);
            exchange.getResponseSender().send("proxyServer");
        });

        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(TestConfigurations.create("https://localhost:" + serverPort))
                .maxNumRetries(0)
                .proxy(createProxySelector("localhost", proxyPort))
                .proxyCredentials(BasicCredentials.of("fakeUser@fake.com", "fake:Password"))
                .build();
        Channel proxiedChannel = create(proxiedConfig);

        try (Response response =
                proxiedChannel.execute(TestEndpoint.POST, request).get()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).hasContent("proxyServer");
        }
    }

    @Test
    public void testNtlmAuthenticatedProxy() throws Exception {
        // Partial test which primarily validates that we've enabled httpclient ntlm support. This does not
        // test the full challenge flow.
        AtomicInteger requestIndex = new AtomicInteger();
        proxyHandler = exchange -> {
            HeaderMap requestHeaders = exchange.getRequestHeaders();
            HeaderMap responseHeaders = exchange.getResponseHeaders();

            switch (requestIndex.getAndIncrement()) {
                case 0:
                    responseHeaders.putAll(Headers.PROXY_AUTHENTICATE, ImmutableList.of("Negotiate", "NTLM"));
                    exchange.setStatusCode(407); // indicates authenticated proxy
                    return;
                case 1:
                    assertThat(requestHeaders.get(Headers.PROXY_AUTHORIZATION))
                            .containsExactly("NTLM TlRMTVNTUAABAAAAAYIIogAAAAAoAAAAAAAAACgAAAAFASgKAAAADw==");
                    new ConnectHandler(ResponseCodeHandler.HANDLE_500).handleRequest(exchange);
                    return;
            }
            throw new IllegalStateException("Expected exactly two requests");
        };
        serverHandler = new BlockingHandler(exchange -> {
            String requestBody = new String(ByteStreams.toByteArray(exchange.getInputStream()), StandardCharsets.UTF_8);
            assertThat(requestBody).isEqualTo(REQUEST_BODY);
            exchange.getResponseSender().send("proxyServer");
        });

        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(TestConfigurations.create("https://localhost:" + serverPort))
                .maxNumRetries(0)
                .proxy(createProxySelector("localhost", proxyPort))
                .proxyCredentials(BasicCredentials.of("fakeUser@fake.com", "fake:Password"))
                .build();
        Channel proxiedChannel = create(proxiedConfig);

        try (Response response =
                proxiedChannel.execute(TestEndpoint.POST, request).get()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).hasContent("proxyServer");
        }
    }

    private static ProxySelector createProxySelector(String host, int port) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI _uri) {
                InetSocketAddress addr = InetSocketAddress.createUnresolved(host, port);
                return ImmutableList.of(new Proxy(Proxy.Type.HTTP, addr));
            }

            @Override
            public void connectFailed(URI _uri, SocketAddress _sa, IOException _ioe) {}
        };
    }
}
