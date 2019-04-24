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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.ByteStreams;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HttpCallbackTest {

    @Mock
    private Observer observer;
    @Mock
    private ErrorDecoder errorDecoder;

    private HttpCallback callback;

    @Before
    public void before() {
        callback = new HttpCallback(observer, errorDecoder);
    }

    @Test
    public void testDelegatesFailure() throws Exception {
        IOException exception = new IOException();
        callback.onFailure(exception);
        verify(observer).exception(exception);
    }

    @Test
    public void testDelegatesSuccessfulResponse() throws Exception {
        callback.onSuccess(httpResponse(200, "body"));

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(observer).success(response.capture());
        assertThat(streamToString(response.getValue().body())).isEqualTo("body");
    }

    @Test
    public void testDelegatesUnsuccessfulResponse() throws Exception {
        RemoteException exception = new RemoteException(
                SerializableError.forException(new ServiceException(ErrorType.INTERNAL)), 500);
        when(errorDecoder.decode(any())).thenReturn(exception);
        callback.onSuccess(httpResponse(400, "body"));

        verify(errorDecoder).decode(any());
        verify(observer).failure(exception);
    }

    @Test
    public void testWhenErrorDecoderThrows_thenExceptionIsObservedAsException() throws Exception {
        Exception exception = new RuntimeException("foo");
        when(errorDecoder.decode(any())).thenThrow(exception);
        callback.onSuccess(httpResponse(400, "body"));

        verify(observer).exception(exception);
    }

    @Test
    public void contentTypeHeaderIsTreatedCaseInsensitively() throws IOException {
        callback.onSuccess(httpResponse(200, "body", Map.of("CoNtEnT-TyPe", List.of("application/json"))));

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(observer).success(response.capture());
        assertThat(response.getValue().contentType()).contains("application/json");
    }

    private static String streamToString(InputStream stream) throws IOException {
        return new String(ByteStreams.toByteArray(stream), StandardCharsets.UTF_8);
    }

    private static HttpResponse<InputStream> httpResponse(int code, String bodyText) {
        return httpResponse(code, bodyText, Map.of("Content-Type", List.of("application/json")));
    }

    private static HttpResponse<InputStream> httpResponse(
            int code, String bodyText, Map<String, List<String>> headers) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return code;
            }

            @Override
            public HttpRequest request() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                throw new UnsupportedOperationException();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (a, b) -> true);
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(bodyText.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public Optional<SSLSession> sslSession() {
                throw new UnsupportedOperationException();
            }

            @Override
            public URI uri() {
                throw new UnsupportedOperationException();
            }

            @Override
            public HttpClient.Version version() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
