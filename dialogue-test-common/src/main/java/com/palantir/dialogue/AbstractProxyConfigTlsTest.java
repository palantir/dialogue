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
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.ConnectHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
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
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractProxyConfigTlsTest {

    private static final Request request = Request.builder()
            .body(new RequestBody() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    output.write("Hello, World".getBytes(StandardCharsets.UTF_8));
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
                .setHandler(new ConnectHandler(ResponseCodeHandler.HANDLE_500))
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

    private static ProxySelector createProxySelector(String host, int port) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI _uri) {
                InetSocketAddress addr = new InetSocketAddress(host, port);
                return ImmutableList.of(new Proxy(Proxy.Type.HTTP, addr));
            }

            @Override
            public void connectFailed(URI _uri, SocketAddress _sa, IOException _ioe) {}
        };
    }
}
