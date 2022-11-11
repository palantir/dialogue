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
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockWebServerExtension.class)
public abstract class AbstractProxyConfigTest {

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

    private final MockWebServer server;

    private final MockWebServer proxyServer;

    protected AbstractProxyConfigTest(MockWebServer server, MockWebServer proxyServer) {
        this.server = server;
        this.proxyServer = proxyServer;
    }

    protected abstract Channel create(ClientConfiguration config);

    @Test
    public void testDirectVersusProxyConnection() throws Exception {
        server.enqueue(new MockResponse().setBody("server"));
        proxyServer.enqueue(new MockResponse().setBody("proxyServer"));

        Channel directChannel = create(TestConfigurations.create("http://localhost:" + server.getPort()));
        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(TestConfigurations.create("http://localhost:" + server.getPort()))
                .proxy(createProxySelector("localhost", proxyServer.getPort()))
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
            assertThat(response.body()).hasContent("proxyServer");
        }
        RecordedRequest proxyRequest = proxyServer.takeRequest();
        assertThat(proxyRequest.getHeader("Host")).isEqualTo("localhost:" + server.getPort());
    }

    @Test
    public void testAuthenticatedProxy() throws Exception {
        proxyServer.enqueue(new MockResponse()
                .addHeader("Proxy-Authenticate", "Basic realm=test")
                .setResponseCode(407)); // indicates authenticated proxy
        proxyServer.enqueue(new MockResponse().setBody("proxyServer"));

        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(TestConfigurations.create("http://localhost:" + server.getPort()))
                .proxy(createProxySelector("localhost", proxyServer.getPort()))
                .proxyCredentials(BasicCredentials.of("fakeUser@fake.com", "fake:Password"))
                .build();
        Channel proxiedChannel = create(proxiedConfig);

        try (Response response =
                proxiedChannel.execute(TestEndpoint.POST, request).get()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).hasContent("proxyServer");
        }
        RecordedRequest firstRequest = proxyServer.takeRequest();
        assertThat(firstRequest.getHeader("Proxy-Authorization")).isNull();
        RecordedRequest secondRequest = proxyServer.takeRequest();
        assertThat(secondRequest.getHeader("Proxy-Authorization"))
                .isEqualTo("Basic ZmFrZVVzZXJAZmFrZS5jb206ZmFrZTpQYXNzd29yZA==");
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
