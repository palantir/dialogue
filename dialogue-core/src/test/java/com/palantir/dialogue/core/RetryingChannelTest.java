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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.ChannelEndpointStage;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.logsafe.exceptions.SafeIoException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RetryingChannelTest {
    private static final TestResponse EXPECTED_RESPONSE = new TestResponse();
    private static final ListenableFuture<Response> SUCCESS = Futures.immediateFuture(EXPECTED_RESPONSE);
    private static final ListenableFuture<Response> FAILED =
            Futures.immediateFailedFuture(new SafeIoException("FAILED"));
    private static final Request REQUEST = Request.builder().build();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChannelEndpointStage channel;

    @Test
    public void testNoFailures() throws ExecutionException, InterruptedException {
        when(channel.endpoint(any()).execute(any())).thenReturn(SUCCESS);

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void testRetriesUpToMaxRetries() throws ExecutionException, InterruptedException {
        when(channel.endpoint(any()).execute(any())).thenReturn(FAILED).thenReturn(SUCCESS);

        // One retry allows an initial request (not a retry) and a single retry.
        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void testRetriesUpToMaxRetriesAndFails() throws ExecutionException, InterruptedException {
        when(channel.endpoint(any()).execute(any()))
                .thenReturn(FAILED)
                .thenReturn(FAILED)
                .thenReturn(SUCCESS);

        // One retry allows an initial request (not a retry) and a single retry.
        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThatThrownBy(response::get)
                .hasRootCauseExactlyInstanceOf(SafeIoException.class)
                .hasRootCauseMessage("FAILED");
    }

    @Test
    public void testRetriesMax() {
        when(channel.endpoint(any()).execute(any())).thenReturn(FAILED);

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThatThrownBy(response::get).hasCauseInstanceOf(SafeIoException.class);
        verify(channel.endpoint(TestEndpoint.POST), times(4)).execute(REQUEST);
    }

    @Test
    public void retries_429s() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(429);
        when(channel.endpoint(any()).execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get())
                .as("After retries are exhausted the 429 response should be returned")
                .isSameAs(mockResponse);
        verify(channel.endpoint(TestEndpoint.POST), times(4)).execute(REQUEST);
    }

    @Test
    public void retries_503s() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(503);
        when(channel.endpoint(any()).execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get())
                .as("After retries are exhausted the 503 response should be returned")
                .isSameAs(mockResponse);
        verify(channel.endpoint(TestEndpoint.POST), times(4)).execute(REQUEST);
    }

    @Test
    public void retries_308s() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(308);
        when(channel.endpoint(any()).execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        long startTime = System.nanoTime();
        Duration backoffSlotSize = Duration.ofSeconds(10);
        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                backoffSlotSize,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get())
                .as("After retries are exhausted the 308 response should be returned")
                .isSameAs(mockResponse);
        verify(channel.endpoint(TestEndpoint.POST), times(4)).execute(REQUEST);
        assertThat(Duration.ofNanos(System.nanoTime() - startTime))
                .as("308 responses should be immediately retried")
                .isLessThan(backoffSlotSize);
    }

    @Test
    public void retries_308s_when_429_and_503_are_propagated() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(308);
        when(channel.endpoint(any()).execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                // This does not apply to 308 responses
                ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get())
                .as("After retries are exhausted the 308 response should be returned")
                .isSameAs(mockResponse);
        verify(channel.endpoint(TestEndpoint.POST), times(4)).execute(REQUEST);
    }

    @Test
    public void propagates_429s_when_requested() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(429);
        when(channel.endpoint(any()).execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(429);
        verify(channel.endpoint(TestEndpoint.POST), times(1)).execute(REQUEST);
    }

    @Test
    public void retries_500s_when_method_is_safe_and_idempotent() throws Exception {
        when(channel.endpoint(any()).execute(any()))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(500)))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(200)));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.endpoint(TestEndpoint.GET).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(200);
        verify(channel.endpoint(TestEndpoint.GET), times(2)).execute(REQUEST);
    }

    @Test
    public void retries_500s_when_method_is_safe_and_idempotent_when_qos_propagated() throws Exception {
        when(channel.endpoint(any()).execute(any()))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(500)))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(200)));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.endpoint(TestEndpoint.GET).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(200);
        verify(channel.endpoint(TestEndpoint.GET), times(2)).execute(REQUEST);
    }

    @Test
    public void doesnt_retry_500s_for_post() throws Exception {
        when(channel.endpoint(any()).execute(any())).thenReturn(Futures.immediateFuture(new TestResponse().code(500)));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(500);
        verify(channel.endpoint(TestEndpoint.POST), times(1)).execute(REQUEST);
    }

    @Test
    public void returns_503s_when_requested() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(503);
        when(channel.endpoint(any()).execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(503);
        verify(channel.endpoint(TestEndpoint.POST), times(1)).execute(REQUEST);
    }

    @Test
    public void response_bodies_are_closed() throws Exception {
        Response response1 = mockResponse(503);
        Response response2 = mockResponse(503);
        Response eventualSuccess = mockResponse(200);

        when(channel.endpoint(any()).execute(any()))
                .thenReturn(Futures.immediateFuture(response1))
                .thenReturn(Futures.immediateFuture(response2))
                .thenReturn(Futures.immediateFuture(eventualSuccess));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response.get(1, TimeUnit.SECONDS).code()).isEqualTo(200);

        verify(response1, times(1)).close();
        verify(response2, times(1)).close();
    }

    @Test
    public void final_exhausted_failure_response_body_is_not_closed() throws Exception {
        TestResponse response1 = new TestResponse().code(503);
        TestResponse response2 = new TestResponse().code(503);
        TestResponse response3 = new TestResponse().code(503);

        when(channel.endpoint(any()).execute(any()))
                .thenReturn(Futures.immediateFuture(response1))
                .thenReturn(Futures.immediateFuture(response2))
                .thenReturn(Futures.immediateFuture(response3));

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                2,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response.get(1, TimeUnit.SECONDS).code()).isEqualTo(503);

        assertThat(response1.isClosed()).isTrue();
        assertThat(response2.isClosed()).isTrue();
        assertThat(response3.isClosed())
                .describedAs("The last response must be left open so we can read the body"
                        + " and deserialize it into a structured error")
                .isFalse();
    }

    @Test
    public void testPropagatesCancel() {
        ListenableFuture<Response> delegateResult = SettableFuture.create();
        when(channel.endpoint(any()).execute(any())).thenReturn(delegateResult);
        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> retryingResult =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(retryingResult.cancel(true)).isTrue();
        assertThat(delegateResult).as("Failed to cancel the delegate future").isCancelled();
    }

    @Test
    public void doesNotRetrySocketTimeout() {
        when(channel.endpoint(any()).execute(any()))
                .thenReturn(Futures.immediateFailedFuture(new SocketTimeoutException()))
                .thenReturn(SUCCESS);

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThatThrownBy(response::get).hasRootCauseExactlyInstanceOf(SocketTimeoutException.class);
    }

    @Test
    public void retriesSocketTimeoutWhenRequested() throws ExecutionException, InterruptedException {
        when(channel.endpoint(any()).execute(any()))
                .thenReturn(Futures.immediateFailedFuture(new SocketTimeoutException()))
                .thenReturn(SUCCESS);

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DANGEROUS_ENABLE_AT_RISK_OF_RETRY_STORMS);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void doesNotRetryRuntimeException() {
        when(channel.endpoint(any()).execute(any()))
                .thenReturn(Futures.immediateFailedFuture(new SafeRuntimeException("bug")))
                .thenReturn(SUCCESS);

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThatThrownBy(response::get)
                .hasRootCauseExactlyInstanceOf(SafeRuntimeException.class)
                .hasRootCauseMessage("bug");
    }

    @Test
    public void retriesSocketTimeout_connectionTimeout() throws ExecutionException, InterruptedException {
        when(channel.endpoint(any()).execute(any()))
                // Magic string allows us to retry on RetryOnTimeout.DISABLED
                .thenReturn(Futures.immediateFailedFuture(new SocketTimeoutException("connect timed out")))
                .thenReturn(SUCCESS);

        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response =
                retryer.endpoint(TestEndpoint.POST).execute(REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void nonRetryableRequestBodyIsNotRetried() throws ExecutionException, InterruptedException {
        when(channel.endpoint(any()).execute(any())).thenReturn(FAILED).thenReturn(SUCCESS);

        // One retry allows an initial request (not a retry) and a single retry.
        ChannelEndpointStage retryer = new RetryingChannel(
                channel,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.endpoint(TestEndpoint.POST)
                .execute(Request.builder()
                        .body(new RequestBody() {
                            @Override
                            public void writeTo(OutputStream _output) {}

                            @Override
                            public String contentType() {
                                return "application/octet-stream";
                            }

                            @Override
                            public boolean repeatable() {
                                return false;
                            }

                            @Override
                            public void close() {}
                        })
                        .build());
        assertThat(response).isDone();
        assertThat(response)
                .as("non-repeatable request bodies should not be retried")
                .isEqualTo(FAILED);
        verify(channel.endpoint(any()), times(1)).execute(any());
    }

    private static Response mockResponse(int status) {
        Response response = mock(Response.class);
        when(response.code()).thenReturn(status);
        return response;
    }
}
