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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.io.ByteStreams;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OkHttpCallbackTest {

    @Mock
    private Observer observer;
    @Mock
    private ErrorDecoder errorDecoder;

    private OkHttpCallback callback;

    @Before
    public void before() {
        callback = new OkHttpCallback(observer, errorDecoder);
    }

    @Test
    public void testDelegatesFailure() throws Exception {
        IOException exception = new IOException();
        callback.onFailure(mock(okhttp3.Call.class), exception);
        verify(observer).exception(exception);
    }

    @Test
    public void testDelegatesSuccessfulResponse() throws Exception {
        callback.onResponse(mock(okhttp3.Call.class), okHttpResponse(200, "body"));

        ArgumentCaptor<Response> response = ArgumentCaptor.forClass(Response.class);
        verify(observer).success(response.capture());
        assertThat(streamToString(response.getValue().body())).isEqualTo("body");
    }

    @Test
    public void testDelegatesUnsuccessfulResponse() throws Exception {
        okhttp3.Response response = okHttpResponse(400, "body");
        RemoteException exception = new RemoteException(
                SerializableError.forException(new ServiceException(ErrorType.INTERNAL)), 500);
        when(errorDecoder.decode(any())).thenReturn(exception);
        callback.onResponse(mock(okhttp3.Call.class), response);

        verify(errorDecoder).decode(any());
        verify(observer).failure(exception);
    }

    @Test
    public void testWhenErrorDecoderThrows_thenExceptionIsObservedAsException() throws Exception {
        Exception exception = new RuntimeException("foo");
        okhttp3.Response response = okHttpResponse(400, "body");
        when(errorDecoder.decode(any())).thenThrow(exception);
        callback.onResponse(mock(okhttp3.Call.class), response);

        verify(observer).exception(exception);
    }

    private static String streamToString(InputStream stream) throws IOException {
        return new String(ByteStreams.toByteArray(stream), StandardCharsets.UTF_8);
    }

    private static okhttp3.Response okHttpResponse(int code, String bodyText) {
        ResponseBody body = ResponseBody.create(MediaType.parse("APPLICATION_JSON"), bodyText);
        Request request = new Request.Builder().url("http://localhost").build();
        return new okhttp3.Response.Builder()
                .protocol(Protocol.HTTP_1_1)
                .message("unused")
                .request(request)
                .code(code)
                .body(body)
                .build();
    }
}
