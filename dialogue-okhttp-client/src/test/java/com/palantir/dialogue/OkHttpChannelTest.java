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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class OkHttpChannelTest {

    // TODO(rfink): We're not actually using the server, kill it. Or use the the server to capture the URLs?
    @Rule
    public final MockWebServer server = new MockWebServer();

    @Mock
    private OkHttpCallback callback;
    @Mock
    private OkHttpClient client;
    @Mock
    private Request request;
    @Mock
    private Observer observer;
    @Mock
    private okhttp3.Call okCall;
    @Mock
    private OkHttpCallback.Factory callFactory;
    private FakeEndpoint endpoint;
    @Mock
    private RequestBody body;

    private OkHttpChannel channel;

    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        when(callFactory.create(any())).thenReturn(callback);
        when(client.newCall(any())).thenReturn(okCall);
        channel = OkHttpChannel.of(client, Urls.https("localhost", server.getPort()), callFactory);

        when(request.body()).thenReturn(Optional.empty());
        when(request.queryParams()).thenReturn(ImmutableMultimap.of());

        endpoint = new FakeEndpoint();
        endpoint.method = HttpMethod.GET;
        endpoint.renderPath = (params, url) -> url.pathSegment("a");
    }

    @Test
    public void respectsBasePath_emptyBasePath() {
        channel = OkHttpChannel.of(client, Urls.create("https", "localhost", server.getPort(), ""), callFactory);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(HttpUrl.get(Urls.https("localhost", server.getPort(), "/a")));
    }

    @Test
    public void respectsBasePath_slashBasePath() {
        channel = OkHttpChannel.of(client, Urls.create("https", "localhost", server.getPort(), "/"), callFactory);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(HttpUrl.get(Urls.https("localhost", server.getPort(), "/a")));
    }

    @Test
    public void respectsBasePath_nonEmptyBasePath() {
        channel = OkHttpChannel.of(
                client, Urls.create("https", "localhost", server.getPort(), "/foo/bar"), callFactory);
        channel.createCall(endpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(
                HttpUrl.get(Urls.https("localhost", server.getPort(), "/foo/bar/a")));
    }

    @Test
    public void respectsBasePath_noSegments() {
        channel = OkHttpChannel.of(
                client, Urls.create("https", "localhost", server.getPort(), "/foo/bar"), callFactory);
        endpoint.renderPath = (params, url) -> { };
        channel.createCall(endpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(
                HttpUrl.get(Urls.https("localhost", server.getPort(), "/foo/bar")));
    }

    @Test
    public void respectsBasePath_emptySegment() {
        channel = OkHttpChannel.of(
                client, Urls.create("https", "localhost", server.getPort(), "/foo/bar"), callFactory);
        endpoint.renderPath = (params, url) -> url.pathSegment("");
        channel.createCall(endpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(
                HttpUrl.get(Urls.https("localhost", server.getPort(), "/foo/bar/")));
    }

    @Test
    public void testUsesRequestParametersToFillPathTemplate() {
        when(request.pathParams()).thenReturn(ImmutableMap.of("a", "A"));
        endpoint.renderPath = (params, url) -> url.pathSegment(params.get("a"));
        channel.createCall(endpoint, request).execute(observer);

        assertThat(captureOkRequest().url()).isEqualTo(HttpUrl.get(Urls.https("localhost", server.getPort(), "/A")));
    }

    @Test
    public void testFillsHeaders() throws Exception {
        when(request.headerParams()).thenReturn(ImmutableMap.of("a", "A", "b", "B"));
        channel.createCall(endpoint, request).execute(observer);

        Headers headers = captureOkRequest().headers();
        assertThat(headers.size()).isEqualTo(2);
        assertThat(headers.get("a")).isEqualTo("A");
        assertThat(headers.get("b")).isEqualTo("B");
    }

    @Ignore("TODO(rfink): Sort our header encoding. How does work in the jaxrs/retrofit clients?")
    @Test
    public void encodesHeaders() throws Exception {
        when(request.headerParams()).thenReturn(ImmutableMap.of("a", "ø\nü"));
        channel.createCall(endpoint, request).execute(observer);

        Headers headers = captureOkRequest().headers();
        assertThat(headers.get("a")).isEqualTo("ø\nü");
    }

    @Test
    public void testFillsQueryParameters() throws Exception {
        String mustEncode = "%^&/?a=A3&a=A4";
        // Edge cases tested: multiple parameters with same name, URL encoding
        when(request.queryParams()).thenReturn(
                ImmutableMultimap.of("a", "A1", "a", "A2", "b", "B", mustEncode, mustEncode));
        channel.createCall(endpoint, request).execute(observer);

        HttpUrl url = captureOkRequest().url();
        Set<String> queryParameters = url.queryParameterNames();
        assertThat(queryParameters.size()).isEqualTo(3);
        assertThat(url.queryParameterValues("a")).containsExactlyInAnyOrder("A1", "A2");
        assertThat(url.queryParameterValues("b")).containsExactlyInAnyOrder("B");
        assertThat(url.queryParameterValues(mustEncode)).containsExactlyInAnyOrder(mustEncode);
    }

    @Test
    public void testGet_failsWhenBodyIsGiven() throws Exception {
        when(request.body()).thenReturn(Optional.of(body));
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("GET endpoints must not have a request body");
    }

    @Test
    public void testPost_failsWhenNoBodyIsGiven() throws Exception {
        endpoint.method = HttpMethod.POST;
        when(request.body()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("POST endpoints must have a request body");
    }

    @Test
    public void testPut_failsWhenNoBodyIsGiven() throws Exception {
        endpoint.method = HttpMethod.PUT;
        when(request.body()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessage("PUT endpoints must have a request body");
    }

    @Test
    public void testGetMethodYieldsGetHttpCall() {
        channel.createCall(endpoint, request).execute(observer);
        assertThat(captureOkRequest().method()).isEqualTo("GET");
    }

    @Test
    public void testExecuteCreatesCallObjectAndEnqueuesCall() {
        channel.createCall(endpoint, request).execute(observer);
        verify(callFactory).create(observer);
        verify(okCall).enqueue(callback);
        verify(okCall, never()).cancel();
    }

    @Test
    public void testCancelDelegatesToOkHttpCall() {
        channel.createCall(endpoint, request).cancel();
        verify(callFactory, never()).create(observer);
        verify(okCall, never()).enqueue(callback);
        verify(okCall).cancel();
    }

    @Test
    public void testCanCancelMultipleTimes() {
        Call call = channel.createCall(endpoint, request);
        call.cancel();
        call.cancel();
        verify(okCall, times(2)).cancel();
    }

    @Test
    public void testExecuteThrowsWhenExecutedTwice() {
        Call call = channel.createCall(endpoint, request);
        call.execute(observer);
        when(okCall.isExecuted()).thenReturn(true);
        assertThatThrownBy(() -> call.execute(observer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Calls must only be executed once.");
    }

    private okhttp3.Request captureOkRequest() {
        ArgumentCaptor<okhttp3.Request> okRequest = ArgumentCaptor.forClass(okhttp3.Request.class);
        verify(client).newCall(okRequest.capture());
        return okRequest.getValue();
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
