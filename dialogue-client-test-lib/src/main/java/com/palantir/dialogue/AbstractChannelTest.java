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

// CHECKSTYLE:OFF  // static import

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.GzipSink;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

// CHECKSTYLE:ON

@SuppressWarnings({"checkstyle:avoidstaticimport", "FutureReturnValueIgnored"})
public abstract class AbstractChannelTest {

    private static final byte[] CONTENT = "test".getBytes(StandardCharsets.UTF_8);

    abstract Channel createChannel(URL baseUrl);

    @Rule
    public final MockWebServer server = new MockWebServer();

    private final RequestBody body = new RequestBody() {
        @Override
        public Optional<Long> length() {
            return Optional.of((long) CONTENT.length);
        }

        @Override
        public InputStream content() {
            return new ByteArrayInputStream(CONTENT);
        }

        @Override
        public String contentType() {
            return "application/text";
        }
    };

    @Mock
    private Request request;

    private FakeEndpoint endpoint;

    private Channel channel;

    @Before
    public void before() {
        channel = createChannel(server.url("").url());

        when(request.body()).thenReturn(Optional.empty());
        when(request.queryParams()).thenReturn(ImmutableMultimap.of());
        server.enqueue(new MockResponse());

        endpoint = new FakeEndpoint();
        endpoint.method = HttpMethod.GET;
        endpoint.renderPath = (params, url) -> url.pathSegment("a");
    }

    @Test
    public void respectsBasePath_emptyBasePath() throws InterruptedException {
        channel = createChannel(server.url("").url());
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/a"));
    }

    @Test
    public void respectsBasePath_slashBasePath() throws InterruptedException {
        channel = createChannel(server.url("/").url());
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/a"));
    }

    @Test
    public void respectsBasePath_nonEmptyBasePath() throws InterruptedException {
        channel = createChannel(server.url("/foo/bar").url());
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar/a"));
    }

    @Test
    public void respectsBasePath_noSegment() throws InterruptedException {
        endpoint.renderPath = (params, url) -> {};

        channel = createChannel(server.url("/foo/bar").url());
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar"));
    }

    @Test
    public void respectsBasePath_emptySegment() throws InterruptedException {
        endpoint.renderPath = (params, url) -> url.pathSegment("");

        channel = createChannel(server.url("/foo/bar").url());
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar/"));
    }

    @Test
    public void usesRequestParametersToFillPathTemplate() throws InterruptedException {
        when(request.pathParams()).thenReturn(ImmutableMap.of("a", "A"));
        endpoint.renderPath = (params, url) -> url.pathSegment(params.get("a"));

        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/A"));
    }

    @Test
    public void encodesPathParameters() throws InterruptedException {
        endpoint.renderPath = (params, url) -> url.pathSegment("/ü/");

        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/%2F%C3%BC%2F"));
    }

    @Test
    public void fillsHeaders() throws Exception {
        when(request.headerParams()).thenReturn(ImmutableMap.of("a", "A", "b", "B"));
        channel.execute(endpoint, request);

        RecordedRequest actualRequest = server.takeRequest();
        assertThat(actualRequest.getHeader("a")).isEqualTo("A");
        assertThat(actualRequest.getHeader("b")).isEqualTo("B");
    }

    @Ignore("TODO(rfink): Sort our header encoding. How does work in the jaxrs/retrofit clients?")
    @Test
    public void encodesHeaders() throws Exception {
        when(request.headerParams()).thenReturn(ImmutableMap.of("a", "ø\nü"));
        channel.execute(endpoint, request);

        RecordedRequest actualRequest = server.takeRequest();
        assertThat(actualRequest.getHeader("a")).isEqualTo("ø\nü");
    }

    @Test
    public void fillsQueryParameters() throws Exception {
        when(request.queryParams()).thenReturn(ImmutableMultimap.of("a", "A1", "a", "A2", "b", "B"));
        channel.execute(endpoint, request);

        HttpUrl requestUrl = server.takeRequest().getRequestUrl();
        Set<String> queryParameters = requestUrl.queryParameterNames();
        assertThat(queryParameters).hasSize(2);
        assertThat(requestUrl.queryParameterValues("a")).containsExactlyInAnyOrder("A1", "A2");
        assertThat(requestUrl.queryParameterValues("b")).containsExactlyInAnyOrder("B");
    }

    @Test
    public void encodesQueryParameters() throws Exception {
        String mustEncode = "%^&/?a=A3&a=A4";
        when(request.queryParams()).thenReturn(ImmutableMultimap.of(mustEncode, mustEncode));
        channel.execute(endpoint, request);

        HttpUrl url = server.takeRequest().getRequestUrl();
        assertThat(url.queryParameterValues(mustEncode)).containsExactlyInAnyOrder(mustEncode);
        assertThat(url.url().getQuery()).isEqualTo("%25%5E%26/?a%3DA3%26a%3DA4=%25%5E%26/?a%3DA3%26a%3DA4");
    }

    @Test
    public void get_failsWhenBodyIsGiven() {
        endpoint.method = HttpMethod.GET;
        when(request.body()).thenReturn(Optional.of(body));
        assertThatThrownBy(() -> channel.execute(endpoint, request))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("GET endpoints must not have a request body");
    }

    @Test
    public void post_okWhenNoBodyIsGiven() {
        endpoint.method = HttpMethod.POST;
        when(request.body()).thenReturn(Optional.empty());
        assertThatCode(() -> channel.execute(endpoint, request)).doesNotThrowAnyException();
    }

    @Test
    public void put_okWhenNoBodyIsGiven() {
        endpoint.method = HttpMethod.PUT;
        when(request.body()).thenReturn(Optional.empty());
        assertThatCode(() -> channel.execute(endpoint, request)).doesNotThrowAnyException();
    }

    @Test
    public void delete_failsWhenBodyIsGiven() {
        endpoint.method = HttpMethod.DELETE;
        when(request.body()).thenReturn(Optional.of(body));
        assertThatThrownBy(() -> channel.execute(endpoint, request))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("DELETE endpoints must not have a request body");
    }

    @Test
    public void getMethodYieldsGetHttpCall() throws InterruptedException {
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getMethod()).isEqualTo("GET");
    }

    @Test
    public void postMethodYieldsPostHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.POST;
        when(request.body()).thenReturn(Optional.of(body));
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getMethod()).isEqualTo("POST");
    }

    @Test
    public void putMethodYieldsPutHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.PUT;
        when(request.body()).thenReturn(Optional.of(body));
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getMethod()).isEqualTo("PUT");
    }

    @Test
    public void deleteMethodYieldsDeleteHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.DELETE;
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getMethod()).isEqualTo("DELETE");
    }

    @Test
    public void setsContentTypeHeader() throws InterruptedException {
        endpoint.method = HttpMethod.POST;
        when(request.body()).thenReturn(Optional.of(body));
        channel.execute(endpoint, request);

        assertThat(server.takeRequest().getHeader("content-type")).isEqualTo("application/text");
    }

    @Test
    public void callObservesSuccessfulResponses() throws ExecutionException, InterruptedException {
        ListenableFuture<Response> call = channel.execute(endpoint, request);
        assertThat(call.get().body()).hasContent("");
        assertThat(call.get().code()).isEqualTo(200);
        assertThat(call.get().headers()).containsEntry("Content-Length", ImmutableList.of("0"));
    }

    @Test
    public void canCancelMultipleTimes() {
        ListenableFuture<Response> call = channel.execute(endpoint, request);
        assertThatCode(() -> call.cancel(true)).doesNotThrowAnyException();
        assertThatCode(() -> call.cancel(true)).doesNotThrowAnyException();
    }

    @Test
    public void callCancellationIsObservedAsException() throws InterruptedException {
        channel.execute(endpoint, request); // drain enqueued response

        channel = createChannel(server.url("").url());
        ListenableFuture<Response> call = channel.execute(endpoint, request);
        call.cancel(true);

        Thread.sleep(1000);
        server.enqueue(new MockResponse());

        assertThatThrownBy(call::get).isInstanceOfAny(CancellationException.class);
    }

    // TODO(rfink): How to test that cancellation propagates to the server?

    @Test
    public void connectionErrorsSurfaceAsExceptions() throws IOException {
        server.shutdown();
        ListenableFuture<Response> call = channel.execute(endpoint, request);
        assertThatThrownBy(call::get).hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    public void supportsGzipEncryptedResponse() throws Exception {
        // drain enqueued response
        channel.execute(endpoint, request).get();
        server.takeRequest();

        server.enqueue(new MockResponse().addHeader("cOntent-encoding", "gzip").setBody(zip("foo")));
        Response response = channel.execute(endpoint, request).get();
        assertThat(response.body()).hasContent("foo");
        assertThat(server.takeRequest().getHeaders().get("accept-encoding")).isEqualTo("gzip");
    }

    private static Buffer zip(String content) throws IOException {
        Buffer gzipBytes = new Buffer();
        Buffer rawBytes = new Buffer();
        rawBytes.writeString(content, StandardCharsets.UTF_8);
        rawBytes.flush();

        GzipSink gzip = new GzipSink(gzipBytes);
        gzip.write(rawBytes, 3);
        gzip.close();
        return gzipBytes;
    }

    private static final class FakeEndpoint implements Endpoint {
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
