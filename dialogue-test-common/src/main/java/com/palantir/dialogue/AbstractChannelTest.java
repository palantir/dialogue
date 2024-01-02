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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.tracing.TestTracing;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.OptionalLong;
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
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

// CHECKSTYLE:ON
@SuppressWarnings({"checkstyle:avoidstaticimport", "checkstyle:VisibilityModifier", "FutureReturnValueIgnored"})
@EnableRuleMigrationSupport
public abstract class AbstractChannelTest {

    protected static final byte[] CONTENT = "test".getBytes(StandardCharsets.UTF_8);

    protected abstract Channel createChannel(ClientConfiguration config);

    private Channel createChannel(URL baseUrl) {
        return createChannel(TestConfigurations.create(baseUrl.toString()));
    }

    @Rule
    public final MockWebServer server = new MockWebServer();

    protected final RequestBody body = new RequestBody() {
        @Override
        public void writeTo(OutputStream output) throws IOException {
            output.write(CONTENT);
        }

        @Override
        public String contentType() {
            return "application/text";
        }

        @Override
        public boolean repeatable() {
            return true;
        }

        @Override
        public OptionalLong contentLength() {
            return OptionalLong.of(CONTENT.length);
        }

        @Override
        public void close() {}
    };

    protected Request request;

    protected FakeEndpoint endpoint;

    protected Channel channel;

    @BeforeEach
    public void before() {
        channel = createChannel(server.url("").url());

        request = Request.builder().build();
        server.enqueue(new MockResponse().setBody("body"));

        endpoint = new FakeEndpoint();
        endpoint.method = HttpMethod.GET;
        endpoint.renderPath = (_params, url) -> url.pathSegment("a");
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
        endpoint.renderPath = (_params, _url) -> {};

        channel = createChannel(server.url("/foo/bar").url());
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar"));
    }

    @Test
    public void respectsBasePath_emptySegment() throws InterruptedException {
        endpoint.renderPath = (_params, url) -> url.pathSegment("");

        channel = createChannel(server.url("/foo/bar").url());
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/foo/bar/"));
    }

    @Test
    public void usesRequestParametersToFillPathTemplate() throws InterruptedException {
        request = Request.builder().from(request).putPathParams("a", "A").build();
        endpoint.renderPath = (params, url) -> url.pathSegment(params.get("a"));

        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/A"));
    }

    @Test
    public void encodesPathParameters() throws InterruptedException {
        endpoint.renderPath = (_params, url) -> url.pathSegment("/ü/");

        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getRequestUrl()).isEqualTo(server.url("/%2F%C3%BC%2F"));
    }

    @Test
    public void fillsHeaders() throws Exception {
        request = Request.builder()
                .from(request)
                .headerParams(ImmutableListMultimap.of("a", "A", "b", "B"))
                .build();
        channel.execute(endpoint, request);

        RecordedRequest actualRequest = server.takeRequest();
        assertThat(actualRequest.getHeader("a")).isEqualTo("A");
        assertThat(actualRequest.getHeader("b")).isEqualTo("B");
    }

    @Test
    @Disabled("TODO(rfink): Sort our header encoding. How does work in the jaxrs/retrofit clients?")
    public void encodesHeaders() throws Exception {
        request = Request.builder().from(request).putHeaderParams("a", "ø\nü").build();
        channel.execute(endpoint, request);

        RecordedRequest actualRequest = server.takeRequest();
        assertThat(actualRequest.getHeader("a")).isEqualTo("ø\nü");
    }

    @Test
    public void fillsQueryParameters() throws Exception {
        request = Request.builder()
                .from(request)
                .queryParams(ImmutableListMultimap.of("a", "A1", "a", "A2", "b", "B"))
                .build();
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
        request = Request.builder()
                .from(request)
                .putQueryParams(mustEncode, mustEncode)
                .build();
        channel.execute(endpoint, request);

        HttpUrl url = server.takeRequest().getRequestUrl();
        assertThat(url.queryParameterValues(mustEncode)).containsExactlyInAnyOrder(mustEncode);
        assertThat(url.url().getQuery()).isEqualTo("%25%5E%26/?a%3DA3%26a%3DA4=%25%5E%26/?a%3DA3%26a%3DA4");
    }

    @Test
    public void get_failsWhenBodyIsGiven() {
        endpoint.method = HttpMethod.GET;
        request = Request.builder().from(request).body(body).build();
        assertThatThrownBy(() -> channel.execute(endpoint, request).get())
                .hasMessageContaining("GET endpoints must not have a request body");
    }

    @Test
    public void head_failsWhenBodyIsGiven() {
        endpoint.method = HttpMethod.HEAD;
        request = Request.builder().from(request).body(body).build();
        assertThatThrownBy(() -> channel.execute(endpoint, request).get())
                .hasMessageContaining("HEAD endpoints must not have a request body");
    }

    @Test
    public void options_failsWhenBodyIsGiven() {
        endpoint.method = HttpMethod.OPTIONS;
        request = Request.builder().from(request).body(body).build();
        assertThatThrownBy(() -> channel.execute(endpoint, request).get())
                .hasMessageContaining("OPTIONS endpoints must not have a request body");
    }

    @Test
    public void head_failsWhenBodyReturned() throws ExecutionException, InterruptedException {
        endpoint.method = HttpMethod.HEAD;
        Response response = channel.execute(endpoint, request).get();
        assertThat(response.body()).hasContent("");
    }

    @Test
    public void post_okWhenNoBodyIsGiven() {
        endpoint.method = HttpMethod.POST;
        assertThatCode(() -> channel.execute(endpoint, request)).doesNotThrowAnyException();
    }

    @Test
    public void put_okWhenNoBodyIsGiven() {
        endpoint.method = HttpMethod.PUT;
        assertThatCode(() -> channel.execute(endpoint, request)).doesNotThrowAnyException();
    }

    @Test
    public void delete_okWhenBodyIsGiven() throws InterruptedException, ExecutionException {
        endpoint.method = HttpMethod.DELETE;
        request = Request.builder().from(request).body(body).build();
        ListenableFuture<Response> result = channel.execute(endpoint, request);
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getBodySize()).isEqualTo(CONTENT.length);
        assertThat(result.get().code()).isEqualTo(200);
    }

    @Test
    public void delete_okWhenNoBodyIsGiven() throws InterruptedException, ExecutionException {
        endpoint.method = HttpMethod.DELETE;
        ListenableFuture<Response> result = channel.execute(endpoint, request);
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getBodySize()).isZero();
        assertThat(result.get().code()).isEqualTo(200);
    }

    @Test
    public void getMethodYieldsGetHttpCall() throws InterruptedException {
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getMethod()).isEqualTo("GET");
    }

    @Test
    public void postMethodYieldsPostHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.POST;
        request = Request.builder().from(request).body(body).build();
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getMethod()).isEqualTo("POST");
    }

    @Test
    public void putMethodYieldsPutHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.PUT;
        request = Request.builder().from(request).body(body).build();
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
    public void headMethodYieldsHeadHttpCall() throws InterruptedException {
        endpoint.method = HttpMethod.HEAD;
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getMethod()).isEqualTo("HEAD");
    }

    @Test
    public void setsContentTypeHeader() throws InterruptedException {
        endpoint.method = HttpMethod.POST;
        request = Request.builder().from(request).body(body).build();
        channel.execute(endpoint, request);

        assertThat(server.takeRequest().getHeader("content-type")).isEqualTo("application/text");
    }

    @Test
    public void callObservesSuccessfulResponses() throws ExecutionException, InterruptedException {
        ListenableFuture<Response> call = channel.execute(endpoint, request);
        assertThat(call.get().body()).hasContent("body");
        assertThat(call.get().code()).isEqualTo(200);
        assertThat(call.get().headers().get(HttpHeaders.CONTENT_LENGTH)).containsExactly("4");
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

        assertThatThrownBy(() -> call.get()).isInstanceOfAny(CancellationException.class);
    }

    // TODO(rfink): How to test that cancellation propagates to the server?

    @Test
    public void connectionErrorsSurfaceAsExceptions() throws IOException {
        server.shutdown();
        ListenableFuture<Response> call = channel.execute(endpoint, request);
        assertThatThrownBy(() -> call.get()).hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    public void supportsGzipEncodedResponse() throws Exception {
        // drain enqueued response
        channel.execute(endpoint, request).get();
        server.takeRequest();

        server.enqueue(new MockResponse().addHeader("cOntent-encoding", "gzip").setBody(zip("foo")));
        Response response = channel.execute(endpoint, request).get();
        assertThat(response.body()).hasContent("foo");
        assertThat(server.takeRequest().getHeaders().get("accept-encoding")).isEqualTo("gzip");
    }

    @Test
    public void supports_empty_path_parameter() throws InterruptedException, ExecutionException {
        endpoint.method = HttpMethod.GET;
        endpoint.renderPath =
                (_params, url) -> url.pathSegment("a").pathSegment("").pathSegment("b");
        ListenableFuture<Response> result = channel.execute(endpoint, request);
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/a//b");
        assertThat(result.get().code()).isEqualTo(200);
    }

    @Test
    public void emptyTrailingPathParameterResultsInTrailingSlash() throws InterruptedException, ExecutionException {
        endpoint.method = HttpMethod.GET;
        endpoint.renderPath = (_params, url) -> url.pathSegment("foo").pathSegment("");
        ListenableFuture<Response> result = channel.execute(endpoint, request);
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/foo/");
        assertThat(result.get().code()).isEqualTo(200);
    }

    @Test
    @TestTracing(snapshot = true)
    public void requestAreTraced() throws Exception {
        endpoint.method = HttpMethod.POST;
        ListenableFuture<Response> result = channel.execute(endpoint, request);
        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getHeader("X-B3-TraceId")).isNotEmpty();
        assertThat(result.get().code()).isEqualTo(200);
    }

    @Test
    public void postIncludesZeroContentLength() throws InterruptedException {
        endpoint.method = HttpMethod.POST;
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getHeader("Content-Length")).isEqualTo("0");
    }

    @Test
    public void putIncludesZeroContentLength() throws InterruptedException {
        endpoint.method = HttpMethod.PUT;
        channel.execute(endpoint, request);
        assertThat(server.takeRequest().getHeader("Content-Length")).isEqualTo("0");
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

    public static final class FakeEndpoint implements Endpoint {
        public BiConsumer<Map<String, String>, UrlBuilder> renderPath;
        public HttpMethod method;

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
