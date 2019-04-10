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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.palantir.conjure.java.dialogue.serde.DefaultErrorDecoder;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.Set;
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
    @Mock
    private Endpoint endpoint;

    private ErrorDecoder errorDecoder = DefaultErrorDecoder.INSTANCE;

    private HttpClient client;
    private HttpChannel channel;

    @Before
    public void before() {
        client = HttpClient.newBuilder().build();
        channel = HttpChannel.of(client, server.url("").url(), errorDecoder);

        when(request.body()).thenReturn(Optional.empty());
        when(endpoint.httpMethod()).thenReturn(HttpMethod.GET);
        when(endpoint.renderPath(any())).thenReturn("/a");
    }

    @Test
    public void endpointPathMustStartWithSlash() {
        when(endpoint.renderPath(any())).thenReturn("");
        assertThatThrownBy(() -> channel.createCall(endpoint, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("endpoint path must start with /");
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
    public void respectsBasePath_emptyEndpointPath() throws InterruptedException {
        when(endpoint.renderPath(any())).thenReturn("/");

        channel = HttpChannel.of(client, server.url("/foo/bar").url(), errorDecoder);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar/"));
    }

    @Test
    public void usesRequestParametersToFillPathTemplate() throws InterruptedException {
        when(request.pathParams()).thenReturn(ImmutableMap.of("a", "A"));
        when(endpoint.renderPath(ImmutableMap.of("a", "A"))).thenReturn("/B");

        channel.createCall(endpoint, request).execute(observer);
        verify(endpoint).renderPath(request.pathParams());
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/B"));
    }

    @Test
    public void fillsHeaders() throws Exception {
        when(request.headerParams()).thenReturn(ImmutableMap.of("a", "A", "b", "B"));
        channel.createCall(endpoint, request).execute(observer);

        RecordedRequest actualRequest = server.takeRequest();
        assertThat(actualRequest.getHeader("a")).isEqualTo("A");
        assertThat(actualRequest.getHeader("b")).isEqualTo("B");
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
        when(endpoint.httpMethod()).thenReturn(HttpMethod.GET);
        when(request.body()).thenReturn(Optional.of(body));
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("GET endpoints must not have a request body");
    }

    @Test
    public void post_failsWhenNoBodyIsGiven() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.POST);
        when(request.body()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Endpoint must have a request body: {method=POST}");
    }

    @Test
    public void put_failsWhenNoBodyIsGiven() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.PUT);
        when(request.body()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("Endpoint must have a request body: {method=PUT}");
    }

    @Test
    public void delete_failsWhenBodyIsGiven() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.DELETE);
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
        when(endpoint.httpMethod()).thenReturn(HttpMethod.POST);
        when(request.body()).thenReturn(Optional.of(body));
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getMethod()).isEqualTo("POST");
    }

    @Test
    public void putMethodYieldsPutHttpCall() throws InterruptedException {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.PUT);
        when(request.body()).thenReturn(Optional.of(body));
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getMethod()).isEqualTo("PUT");
    }

    @Test
    public void deleteMethodYieldsDeleteHttpCall() throws InterruptedException {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.DELETE);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(server.takeRequest().getMethod()).isEqualTo("DELETE");
    }
}
