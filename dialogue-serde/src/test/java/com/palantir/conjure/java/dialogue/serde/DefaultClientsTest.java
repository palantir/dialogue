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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
import com.palantir.dialogue.blocking.CallingThreadExecutorAssert;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class DefaultClientsTest {

    private static final String VALUE = "value";

    @Mock
    private Channel channel;

    @Mock
    private Endpoint endpoint;

    @Mock
    private Deserializer<String> stringDeserializer;

    @Captor
    private ArgumentCaptor<Request> requestCaptor;

    private Response response = new TestResponse();
    private BodySerDe bodySerde = new ConjureBodySerDe(
            DefaultConjureRuntime.DEFAULT_ENCODINGS,
            ErrorDecoder.INSTANCE,
            Encodings.emptyContainerDeserializer(),
            DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
    private final SettableFuture<Response> responseFuture = SettableFuture.create();
    private final ListeningExecutorService executor =
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

    enum CallType {
        Blocking,
        Async
    }

    @ParameterizedTest
    @EnumSource(CallType.class)
    public void testCall(CallType callType) {
        Request request = Request.builder().build();
        when(stringDeserializer.deserialize(eq(response))).thenReturn(VALUE);
        when(channel.execute(eq(endpoint), eq(request))).thenReturn(responseFuture);
        ListenableFuture<String> result = call(callType, request);
        assertThat(result).isNotDone();
        responseFuture.set(response);

        assertStringResult(callType, result);
    }

    @ParameterizedTest
    @EnumSource(CallType.class)
    public void testAddsAcceptHeader(CallType callType) {
        String expectedAccept = "application/json";
        Request request = Request.builder().build();

        when(stringDeserializer.deserialize(eq(response))).thenReturn("value");
        when(stringDeserializer.accepts()).thenReturn(Optional.of(expectedAccept));
        when(channel.execute(eq(endpoint), any())).thenReturn(Futures.immediateFuture(response));

        ListenableFuture<String> result = call(callType, request);

        assertStringResult(callType, result);
        verify(channel).execute(eq(endpoint), requestCaptor.capture());
        assertThat(requestCaptor.getValue().headerParams().get(HttpHeaders.ACCEPT))
                .containsExactly(expectedAccept);
    }

    @ParameterizedTest
    @EnumSource(CallType.class)
    public void testCallClosesRequestOnCompletion_success(CallType callType) {
        RequestBody body = mock(RequestBody.class);
        Request request = Request.builder().body(body).build();
        when(stringDeserializer.deserialize(eq(response))).thenReturn("value");
        when(channel.execute(eq(endpoint), eq(request))).thenReturn(responseFuture);
        ListenableFuture<String> result = call(callType, request);

        // The request has been sent, but not yet completed
        verifyExecutionStarted(request);
        verify(body, never()).close();

        // Upon completion the request should be closed
        responseFuture.set(response);
        assertStringResult(callType, result);
        verify(body).close();
    }

    @ParameterizedTest
    @EnumSource(CallType.class)
    public void testBinaryResponse_inputStreamRemainsUnclosed(CallType callType) throws IOException {
        when(channel.execute(eq(endpoint), any())).thenReturn(responseFuture);

        ListenableFuture<InputStream> future =
                call(callType, Request.builder().build(), bodySerde.inputStreamDeserializer());

        TestResponse testResponse = new TestResponse().contentType("application/octet-stream");
        responseFuture.set(testResponse);

        try (CloseRecordingInputStream inputStream = (CloseRecordingInputStream) Futures.getUnchecked(future)) {
            assertThat(inputStream.available())
                    .describedAs("Content should be empty")
                    .isEqualTo(0);
            inputStream.assertNotClosed();
            assertThat(testResponse.isClosed()).describedAs("Response").isFalse();
        }

        assertThat(testResponse.body().isClosed())
                .describedAs("User has closed it now")
                .isTrue();
        assertThat(testResponse.isClosed())
                .describedAs(
                        "Response#close was never called, but no big deal because the body is the only resource worth"
                                + " closing")
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(CallType.class)
    public void testOptionalBinaryResponse_inputStreamRemainsUnclosed(CallType callType) throws IOException {
        when(channel.execute(eq(endpoint), any())).thenReturn(responseFuture);

        ListenableFuture<Optional<InputStream>> future =
                call(callType, Request.builder().build(), bodySerde.optionalInputStreamDeserializer());

        TestResponse testResponse = new TestResponse().contentType("application/octet-stream");
        responseFuture.set(testResponse);

        Optional<InputStream> maybeInputStream = Futures.getUnchecked(future);
        try (CloseRecordingInputStream inputStream = (CloseRecordingInputStream) maybeInputStream.get()) {
            assertThat(inputStream.available())
                    .describedAs("Content should be empty")
                    .isEqualTo(0);
            inputStream.assertNotClosed();
            assertThat(testResponse.isClosed()).describedAs("Response").isFalse();
        }

        assertThat(testResponse.body().isClosed())
                .describedAs("User has closed it now")
                .isTrue();
        assertThat(testResponse.isClosed())
                .describedAs(
                        "Response#close was never called, but no big deal because the body is the only resource worth"
                                + " closing")
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(CallType.class)
    public void testCallClosesRequestOnCompletion_failure(CallType callType) {
        RequestBody body = mock(RequestBody.class);
        Request request = Request.builder().body(body).build();
        when(channel.execute(eq(endpoint), eq(request))).thenReturn(responseFuture);
        ListenableFuture<String> result = call(callType, request);

        // The request has been sent, but not yet completed
        verifyExecutionStarted(request);
        verify(body, never()).close();

        // Upon completion the request should be closed
        IllegalStateException exception = new IllegalStateException();
        responseFuture.setException(exception);
        assertThat(result)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCause(exception);
        verify(body).close();
    }

    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    public void testCallBlockingPropagatesCallingThreadExecutor() {
        Request request = Request.builder().build();
        when(stringDeserializer.deserialize(eq(response))).thenReturn(VALUE);
        when(channel.execute(eq(endpoint), requestCaptor.capture())).thenReturn(responseFuture);
        ListenableFuture<String> result = call(CallType.Blocking, request);
        assertThat(result).isNotDone();
        responseFuture.set(response);

        assertStringResult(CallType.Blocking, result);

        CallingThreadExecutorAssert.assertUsingCallingThreadExecutor(requestCaptor.getValue())
                .isNotNull();
    }

    private ListenableFuture<String> call(CallType callType, Request request) {
        return call(callType, request, stringDeserializer);
    }

    private <T> ListenableFuture<T> call(CallType callType, Request request, Deserializer<T> deserializer) {
        switch (callType) {
            case Async:
                return DefaultClients.INSTANCE.call(channel, endpoint, request, deserializer);
            case Blocking:
                return executor.submit(() -> DefaultClients.INSTANCE.callBlocking(
                        request1 -> channel.execute(endpoint, request1), request, deserializer));
            default:
                throw new SafeIllegalStateException("Unknown call type");
        }
    }

    private void assertStringResult(CallType callType, ListenableFuture<String> result) {
        if (callType == CallType.Async) {
            assertThat(result).isDone();
        }
        try {
            assertThat(result.get()).isEqualTo(VALUE);
        } catch (InterruptedException | ExecutionException e) {
            throw new SafeRuntimeException(e);
        }
    }

    private void verifyExecutionStarted(Request request) {
        verify(channel, timeout(500)).execute(eq(endpoint), eq(request));
    }
}
