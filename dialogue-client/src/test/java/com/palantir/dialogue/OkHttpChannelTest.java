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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.palantir.dialogue.api.Observer;
import com.palantir.logsafe.SafeLoggable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class OkHttpChannelTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Mock
    private OkHttpCallback callback;
    @Mock
    private OkHttpClient client;
    @Mock
    private Request<String> request;
    @Mock
    private Observer<String> observer;
    @Mock
    private okhttp3.Call okCall;
    @Mock
    private OkHttpCallback.Factory callFactory;
    @Mock
    private Endpoint<String, String> getEndpoint;
    @Mock
    private Endpoint<String, String> endpoint;
    @Mock
    private Serializer<String> serializer;

    private OkHttpChannel channel;

    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        when(callFactory.create(any(), any())).thenReturn(callback);
        when(client.newCall(any())).thenReturn(okCall);
        channel = OkHttpChannel.of(client, Urls.https("localhost", server.getPort()), callFactory);

        when(request.body()).thenReturn(Optional.empty());
        when(request.queryParams()).thenReturn(ImmutableMultimap.of());

        when(getEndpoint.httpMethod()).thenReturn(HttpMethod.GET);
        when(getEndpoint.renderPath(any())).thenReturn("/a");
        when(endpoint.renderPath(any())).thenReturn("/a");

        when(endpoint.requestSerializer()).thenReturn(serializer);
        when(serializer.serialize("bodyString")).thenReturn("serializedBodyString".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void endpointPathMustStartWithSlash() {
        when(endpoint.renderPath(any())).thenReturn("");
        assertThatThrownBy(() -> channel.createCall(endpoint, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("endpoint path must start with /");
    }

    @Test
    public void respectsBasePath_emptyBasePath() {
        channel = OkHttpChannel.of(client, Urls.create("https", "localhost", server.getPort(), ""));
        channel.createCall(getEndpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(HttpUrl.get(Urls.https("localhost", server.getPort(), "/a")));
    }

    @Test
    public void respectsBasePath_slashBasePath() {
        channel = OkHttpChannel.of(client, Urls.create("https", "localhost", server.getPort(), "/"));
        channel.createCall(getEndpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(HttpUrl.get(Urls.https("localhost", server.getPort(), "/a")));
    }

    @Test
    public void respectsBasePath_nonEmptyBasePath() {
        channel = OkHttpChannel.of(client, Urls.create("https", "localhost", server.getPort(), "/foo/bar"));
        channel.createCall(getEndpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(
                HttpUrl.get(Urls.https("localhost", server.getPort(), "/foo/bar/a")));
    }

    @Test
    public void respectsBasePath_emptyEndpointPath() {
        channel = OkHttpChannel.of(client, Urls.create("https", "localhost", server.getPort(), "/foo/bar"));
        when(getEndpoint.renderPath(any())).thenReturn("/");
        channel.createCall(getEndpoint, request).execute(observer);
        assertThat(captureOkRequest().url()).isEqualTo(
                HttpUrl.get(Urls.https("localhost", server.getPort(), "/foo/bar/")));
    }

    @Test
    public void testUsesRequestParametersToFillPathTemplate() {
        when(request.pathParams()).thenReturn(ImmutableMap.of("a", "A"));
        channel.createCall(getEndpoint, request).execute(observer);

        verify(getEndpoint).renderPath(request.pathParams());
        assertThat(captureOkRequest().url()).isEqualTo(HttpUrl.get(Urls.https("localhost", server.getPort(), "/a")));
    }

    @Test
    public void testFillsHeaders() throws Exception {
        when(request.headerParams()).thenReturn(ImmutableMap.of("a", "A", "b", "B"));
        channel.createCall(getEndpoint, request).execute(observer);

        Headers headers = captureOkRequest().headers();
        assertThat(headers.size()).isEqualTo(2);
        assertThat(headers.get("a")).isEqualTo("A");
        assertThat(headers.get("b")).isEqualTo("B");
    }

    @Test
    public void testFillsQueryParameters() throws Exception {
        String mustEncode = "%^&/?a=A3&a=A4";
        // Edge cases tested: multiple parameters with same name, URL encoding
        when(request.queryParams()).thenReturn(
                ImmutableMultimap.of("a", "A1", "a", "A2", "b", "B", mustEncode, mustEncode));
        channel.createCall(getEndpoint, request).execute(observer);

        HttpUrl url = captureOkRequest().url();
        Set<String> queryParameters = url.queryParameterNames();
        assertThat(queryParameters.size()).isEqualTo(3);
        assertThat(url.queryParameterValues("a")).containsExactlyInAnyOrder("A1", "A2");
        assertThat(url.queryParameterValues("b")).containsExactlyInAnyOrder("B");
        assertThat(url.queryParameterValues(mustEncode)).containsExactlyInAnyOrder(mustEncode);
    }

    @Test
    public void testGet_failsWhenBodyIsGiven() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.GET);
        when(request.body()).thenReturn(Optional.of("bodyString"));
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("GET endpoints must not have a request body");
    }

    @Test
    public void testGet_doesNotUseSerializerWhenNoBodyIsGiven() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.GET);
        when(request.body()).thenReturn(Optional.empty());
        channel.createCall(endpoint, request).execute(observer);

        verify(serializer, never()).serialize(any());
        assertThat(captureOkRequest().body()).isNull();
    }

    @Test
    public void testPost_usesSerializerForBody() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.POST);
        when(request.body()).thenReturn(Optional.of("bodyString"));
        channel.createCall(endpoint, request).execute(observer);

        verify(serializer).serialize("bodyString");
        assertThat(captureOkRequest().body().contentLength()).isEqualTo("serializedBodyString".length());
    }

    @Test
    public void testPost_failsWhenNoBodyIsGiven() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.POST);
        when(request.body()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(RuntimeException.class)
                .isInstanceOf(SafeLoggable.class)
                .hasMessage("Endpoint must have a request body: {method=POST}");
    }

    @Test
    public void testPut_usesSerializerForBody() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.PUT);
        when(request.body()).thenReturn(Optional.of("bodyString"));
        channel.createCall(endpoint, request).execute(observer);

        verify(serializer).serialize("bodyString");
        assertThat(captureOkRequest().body().contentLength()).isEqualTo("serializedBodyString".length());
    }

    @Test
    public void testPut_failsWhenNoBodyIsGiven() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.PUT);
        when(request.body()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> channel.createCall(endpoint, request).execute(observer))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Endpoint must have a request body: {method=PUT}");
    }

    @Test
    public void testDelete_usesSerializerForBody() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.DELETE);
        when(request.body()).thenReturn(Optional.of("bodyString"));
        channel.createCall(endpoint, request).execute(observer);

        verify(serializer).serialize("bodyString");
        assertThat(captureOkRequest().body().contentLength()).isEqualTo("serializedBodyString".length());
    }

    @Test
    public void testDelete_doesNotUseSerializerWhenNoBodyIsGiven() throws Exception {
        when(endpoint.httpMethod()).thenReturn(HttpMethod.DELETE);
        when(request.body()).thenReturn(Optional.empty());
        channel.createCall(endpoint, request).execute(observer);

        verify(serializer, never()).serialize(any());
        assertThat(captureOkRequest().body().contentLength()).isEqualTo(0);
    }

    @Test
    public void testGetMethodYieldsGetHttpCall() {
        channel.createCall(getEndpoint, request).execute(observer);
        assertThat(captureOkRequest().method()).isEqualTo("GET");
    }

    @Test
    public void testExecuteCreatesCallObjectAndEnqueuesCall() {
        channel.createCall(getEndpoint, request).execute(observer);
        verify(callFactory).create(getEndpoint, observer);
        verify(okCall).enqueue(callback);
        verify(okCall, never()).cancel();
    }

    @Test
    public void testCancelDelegatesToOkHttpCall() {
        channel.createCall(getEndpoint, request).cancel();
        verify(callFactory, never()).create(getEndpoint, observer);
        verify(okCall, never()).enqueue(callback);
        verify(okCall).cancel();
    }

    @Test
    public void testCanCancelMultipleTimes() {
        Call<?> call = channel.createCall(getEndpoint, request);
        call.cancel();
        call.cancel();
        verify(okCall, times(2)).cancel();
    }

    @Test
    public void testExecuteThrowsWhenExecutedTwice() {
        Call<String> call = channel.createCall(getEndpoint, request);
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

    @SafeVarargs
    private static <T> List<T> list(T... objects) {
        return ImmutableList.copyOf(objects);
    }
}
