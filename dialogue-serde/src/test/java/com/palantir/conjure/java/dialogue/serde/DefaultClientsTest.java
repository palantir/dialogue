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

package com.palantir.conjure.java.dialogue.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.CloseRecordingInputStream;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class DefaultClientsTest {

    @Mock
    private Channel channel;

    @Mock
    private Endpoint endpoint;

    @Mock
    private Deserializer<String> deserializer;

    @Captor
    private ArgumentCaptor<Request> requestCaptor;

    private Response response = new TestResponse();
    private BodySerDe bodySerde = new ConjureBodySerDe(DefaultConjureRuntime.DEFAULT_ENCODINGS);
    private final SettableFuture<Response> responseFuture = SettableFuture.create();

    @Test
    public void testCall() throws ExecutionException, InterruptedException {
        Request request = Request.builder().build();
        when(deserializer.deserialize(eq(response))).thenReturn("value");
        when(channel.execute(eq(endpoint), eq(request))).thenReturn(responseFuture);
        ListenableFuture<String> result = DefaultClients.INSTANCE.call(channel, endpoint, request, deserializer);
        assertThat(result).isNotDone();
        responseFuture.set(response);
        assertThat(result).isDone();
        assertThat(result.get()).isEqualTo("value");
    }

    @Test
    public void testAddsAcceptHeader() throws ExecutionException, InterruptedException {
        String expectedAccept = "application/json";
        Request request = Request.builder().build();

        when(deserializer.deserialize(eq(response))).thenReturn("value");
        when(deserializer.accepts()).thenReturn(Optional.of(expectedAccept));
        when(channel.execute(eq(endpoint), any())).thenReturn(Futures.immediateFuture(response));

        ListenableFuture<String> result = DefaultClients.INSTANCE.call(channel, endpoint, request, deserializer);

        assertThat(result).isDone();
        assertThat(result.get()).isEqualTo("value");
        verify(channel).execute(eq(endpoint), requestCaptor.capture());
        assertThat(requestCaptor.getValue().headerParams().get(HttpHeaders.ACCEPT))
                .containsExactly(expectedAccept);
    }

    @Test
    public void testCallClosesRequestOnCompletion_success() {
        RequestBody body = mock(RequestBody.class);
        Request request = Request.builder().body(body).build();
        when(deserializer.deserialize(eq(response))).thenReturn("value");
        when(channel.execute(eq(endpoint), eq(request))).thenReturn(responseFuture);
        ListenableFuture<String> result = DefaultClients.INSTANCE.call(channel, endpoint, request, deserializer);

        // The request has been sent, but not yet completed
        verify(body, never()).close();

        // Upon completion the request should be closed
        responseFuture.set(response);
        assertThat(result).isDone();
        verify(body).close();
    }

    @Test
    public void testBinaryResponse_inputStreamRemainsUnclosed() throws IOException {
        when(channel.execute(eq(endpoint), any())).thenReturn(responseFuture);

        ListenableFuture<InputStream> future = DefaultClients.INSTANCE.call(
                channel, endpoint, Request.builder().build(), bodySerde.inputStreamDeserializer());

        TestResponse testResponse = new TestResponse().contentType("application/octet-stream");
        responseFuture.set(testResponse);

        try (InputStream inputStream = Futures.getUnchecked(future)) {
            assertThat(inputStream.available())
                    .describedAs("Content should be empty")
                    .isEqualTo(0);
            asCloseRecording(inputStream).assertNotClosed();
            assertThat(testResponse.isClosed())
                    .describedAs("It's ok for the Response to remain open for now, it'll be closed when the user "
                            + "closes the InputStream")
                    .isFalse();
        }

        assertThat(testResponse.body().isClosed())
                .describedAs("User has closed it now")
                .isTrue();
        assertThat(testResponse.isClosed())
                .describedAs("Closing the InputStream also closed the Response")
                .isTrue();
    }

    @Test
    public void testOptionalBinaryResponse_inputStreamRemainsUnclosed() throws IOException {
        when(channel.execute(eq(endpoint), any())).thenReturn(responseFuture);

        ListenableFuture<Optional<InputStream>> future = DefaultClients.INSTANCE.call(
                channel, endpoint, Request.builder().build(), bodySerde.optionalInputStreamDeserializer());

        TestResponse testResponse = new TestResponse().contentType("application/octet-stream");
        responseFuture.set(testResponse);

        Optional<InputStream> maybeInputStream = Futures.getUnchecked(future);
        try (InputStream inputStream = maybeInputStream.get()) {
            assertThat(inputStream.available())
                    .describedAs("Content should be empty")
                    .isEqualTo(0);
            asCloseRecording(inputStream).assertNotClosed();
            assertThat(testResponse.isClosed())
                    .describedAs("It's ok for the Response to remain open for now, it'll be closed when the user "
                            + "closes the InputStream")
                    .isFalse();
        }

        assertThat(testResponse.body().isClosed())
                .describedAs("User has closed it now")
                .isTrue();
        assertThat(testResponse.isClosed())
                .describedAs("Closing the InputStream also closed the Response")
                .isTrue();
    }

    @Test
    public void testCallClosesRequestOnCompletion_failure() {
        RequestBody body = mock(RequestBody.class);
        Request request = Request.builder().body(body).build();
        when(channel.execute(eq(endpoint), eq(request))).thenReturn(responseFuture);
        ListenableFuture<String> result = DefaultClients.INSTANCE.call(channel, endpoint, request, deserializer);

        // The request has been sent, but not yet completed
        verify(body, never()).close();

        // Upon completion the request should be closed
        responseFuture.setException(new IllegalStateException());
        assertThat(result).isDone();
        verify(body).close();
    }

    private static CloseRecordingInputStream asCloseRecording(InputStream inputStream) {
        return (CloseRecordingInputStream) ((ForwardingInputStream) inputStream).delegate();
    }
}
