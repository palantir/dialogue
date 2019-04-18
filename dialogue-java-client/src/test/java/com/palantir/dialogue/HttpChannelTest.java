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

package com.palantir.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.palantir.conjure.java.dialogue.serde.DefaultErrorDecoder;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class HttpChannelTest {

    @Rule
    public final MockWebServer server = new MockWebServer();
    private final RequestBody body = new RequestBody() {
        @Override
        public Optional<Long> length() {
            return Optional.empty();
        }

        @Override
        public InputStream content() {
            return new ByteArrayInputStream(new byte[] {});
        }

        @Override
        public String contentType() {
            return "unused";
        }
    };

    @Mock
    private Request request;
    @Mock
    private Observer observer;
    private FakeEndpoint endpoint;

    private ErrorDecoder errorDecoder = DefaultErrorDecoder.INSTANCE;

    private HttpClient client;
    private HttpChannel channel;

    @Before
    public void before() {
        client = HttpClient.newBuilder().build();
        channel = HttpChannel.of(client, server.url("").url(), errorDecoder);

        when(request.body()).thenReturn(Optional.empty());

        endpoint = new FakeEndpoint();
        endpoint.method = HttpMethod.GET;
        endpoint.renderPath = (params, url) -> url.pathSegment("a");
    }

    @Test
    public void respectsBasePath_emptyBasePath() throws InterruptedException {
        channel = HttpChannel.of(client, server.url("").url(), errorDecoder);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/a"));
    }

    @Test
    public void respectsBasePath_slashBasePath() throws InterruptedException {
        channel = HttpChannel.of(client, server.url("/").url(), errorDecoder);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/a"));
    }

    @Test
    public void respectsBasePath_nonEmptyBasePath() throws InterruptedException {
        channel = HttpChannel.of(client, server.url("/foo/bar").url(), errorDecoder);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar/a"));
    }

    @Test
    public void respectsBasePath_noSegment() throws InterruptedException {
        endpoint.renderPath = (params, url) -> { };

        channel = HttpChannel.of(client, server.url("/foo/bar").url(), errorDecoder);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar"));
    }

    @Test
    public void respectsBasePath_emptySegment() throws InterruptedException {
        endpoint.renderPath = (params, url) -> url.pathSegment("");

        channel = HttpChannel.of(client, server.url("/foo/bar").url(), errorDecoder);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar/"));
    }

    @Test
    public void usesRequestParametersToFillPathTemplate() throws InterruptedException {
        when(request.pathParams()).thenReturn(ImmutableMap.of("a", "A"));
        endpoint.renderPath = (params, url) -> url.pathSegment(params.get("a"));

        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/A"));
    }

    @Test
    public void fillsHeaders() throws Exception {
        when(request.headerParams()).thenReturn(ImmutableMap.of("a", "A", "b", "B"));
        channel.createCall(endpoint, request).execute(observer);

        RecordedRequest actualRequest = server.takeRequest();
        assertThat(actualRequest.getHeader("a")).isEqualTo("A");
        assertThat(actualRequest.getHeader("b")).isEqualTo("B");
    }

    @Ignore("TODO(rfink): Sort our header encoding. How does work in the jaxrs/retrofit clients?")
    @Test
    public void encodesHeaders() throws Exception {
        when(request.headerParams()).thenReturn(ImmutableMap.of("a", "ø\nü"));
        channel.createCall(endpoint, request).execute(observer);

        RecordedRequest actualRequest = server.takeRequest();
        assertThat(actualRequest.getHeader("a")).isEqualTo("ø\nü");
    }

    @Ignore("TODO(rfink): Implement query params")
    @Test
    public void fillsQueryParameters() throws Exception {
        String mustEncode = "%^&/?a=A3&a=A4";
        // Edge cases tested: multiple parameters with same name, URL encoding
        when(request.queryParams()).thenReturn(
                ImmutableMultimap.of("a", "A1", "a", "A2", "b", "B", mustEncode, mustEncode));
        channel.createCall(endpoint, request).execute(observer);

        HttpUrl requestUrl = server.takeRequest().getRequestUrl();
        Set<String> queryParameters = requestUrl.queryParameterNames();
        assertThat(queryParameters.size()).isEqualTo(3);
        assertThat(requestUrl.queryParameterValues("a")).containsExactlyInAnyOrder("A1", "A2");
        assertThat(requestUrl.queryParameterValues("b")).containsExactlyInAnyOrder("B");
        assertThat(requestUrl.queryParameterValues(mustEncode)).containsExactlyInAnyOrder(mustEncode);
    }

    @Test
    public void get_failsWhenBodyIsGiven() throws Exception {
        endpoint.method = HttpMethod.GET;
        when(request.body()).thenReturn(Optional.of(body));
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("GET endpoints must not have a request body");
    }

    @Test
    public void post_failsWhenNoBodyIsGiven() throws Exception {
        endpoint.method = HttpMethod.POST;
        when(request.body()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Endpoint must have a request body: {method=POST}");
    }

    @Test
    public void put_failsWhenNoBodyIsGiven() throws Exception {
        endpoint.method = HttpMethod.PUT;
        when(request.body()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Endpoint must have a request body: {method=PUT}");
    }

    @Test
    public void delete_failsWhenBodyIsGiven() throws Exception {
        endpoint.method = HttpMethod.DELETE;
        when(request.body()).thenReturn(Optional.of(body));
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("DELETE endpoints must not have a request body");
    }

    @Test
    public void getMethodYieldsGetHttpCall() throws InterruptedException {
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getMethod()).isEqualTo("GET");
    }

    @Test
    public void postMethodYieldsPostHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.POST;
        when(request.body()).thenReturn(Optional.of(body));
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getMethod()).isEqualTo("POST");
    }

    @Test
    public void putMethodYieldsPutHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.PUT;
        when(request.body()).thenReturn(Optional.of(body));
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getMethod()).isEqualTo("PUT");
    }

    @Test
    public void deleteMethodYieldsDeleteHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.DELETE;
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getMethod()).isEqualTo("DELETE");
    }

    @Test
    public void encodesPathAndQueryParameters() {

    }

    private static class FakeEndpoint implements Endpoint {
        private BiConsumer<Map<String, String>, UrlBuilder> renderPath;
        private HttpMethod method;

        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {
            renderPath.accept(params, url);
        }

        @Override
        public HttpMethod httpMethod() {
            return method;
        }
    }
}
