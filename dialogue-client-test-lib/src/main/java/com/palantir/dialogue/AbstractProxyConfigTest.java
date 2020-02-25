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
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;

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
            })
            .build();

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Rule
    public final MockWebServer proxyServer = new MockWebServer();

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
                directChannel.execute(FakeEndpoint.INSTANCE, request).get()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).hasContent("server");
        }
        try (Response response =
                proxiedChannel.execute(FakeEndpoint.INSTANCE, request).get()) {
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
                .proxyCredentials(BasicCredentials.of("fakeUser", "fakePassword"))
                .build();
        Channel proxiedChannel = create(proxiedConfig);

        try (Response response =
                proxiedChannel.execute(FakeEndpoint.INSTANCE, request).get()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).hasContent("proxyServer");
        }
        RecordedRequest firstRequest = proxyServer.takeRequest();
        assertThat(firstRequest.getHeader("Proxy-Authorization")).isNull();
        RecordedRequest secondRequest = proxyServer.takeRequest();
        assertThat(secondRequest.getHeader("Proxy-Authorization")).isEqualTo("Basic ZmFrZVVzZXI6ZmFrZVBhc3N3b3Jk");
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
