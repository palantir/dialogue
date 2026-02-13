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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.QosReason.DueTo;
import com.palantir.conjure.java.api.errors.QosReason.RetryHint;
import com.palantir.conjure.java.api.errors.QosReasons;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TestResponseQosEncoder;
import com.palantir.dialogue.core.DialogueClientMetrics.RequestRetryCount_Result;
import com.palantir.logsafe.exceptions.SafeIoException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;

@ExtendWith(MockitoExtension.class)
public class RetryingChannelTest {
    private static final TestResponse EXPECTED_RESPONSE = new TestResponse();
    private static final ListenableFuture<Response> SUCCESS = Futures.immediateFuture(EXPECTED_RESPONSE);
    private static final ListenableFuture<Response> FAILED =
            Futures.immediateFailedFuture(new SafeIoException("FAILED"));
    private static final Request REQUEST = Request.builder().build();

    @Mock
    private EndpointChannel channel;

    private TaggedMetricRegistry registry;

    @BeforeEach
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
    }

    @Test
    public void testNoFailures() throws ExecutionException, InterruptedException {
        when(channel.execute(any())).thenReturn(SUCCESS);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void testRetriesUpToMaxRetries() throws ExecutionException, InterruptedException {
        when(channel.execute(any())).thenReturn(FAILED).thenReturn(SUCCESS);

        // One retry allows an initial request (not a retry) and a single retry.
        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void testProxyUpstreamRequestAttemptsAccumulated() {
        when(channel.execute(any()))
                .thenAnswer((Answer<ListenableFuture<Response>>) _invocation -> Futures.immediateFuture(
                        new TestResponse().code(503).withHeader(Responses.PROXY_UPSTREAM_REQUEST_ATTEMPTS, "2")))
                .thenReturn(FAILED)
                // the success case should never be reached
                .thenReturn(SUCCESS);

        // One retry allows an initial request (not a retry) and a single retry.
        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                2,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        // Our first request failed (1 failure) and accumulated a second from the 'Proxy-Retry-Attempts: 2' response
        // header, so the next request which results in a SafeIoException exceeds the limit and is not retried.
        assertThat(response)
                .failsWithin(Duration.ZERO)
                .withThrowableThat()
                // Bypass the outer ExecutionException implementation detail of Future
                .havingCause()
                .isInstanceOf(SafeIoException.class)
                .withMessage("FAILED");
    }

    @Test
    public void testRetriesUpToMaxRetriesAndFails() throws ExecutionException, InterruptedException {
        when(channel.execute(any())).thenReturn(FAILED).thenReturn(FAILED).thenReturn(SUCCESS);

        // One retry allows an initial request (not a retry) and a single retry.
        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThatThrownBy(response::get)
                .hasRootCauseExactlyInstanceOf(SafeIoException.class)
                .hasRootCauseMessage("FAILED");
    }

    @Test
    public void testRetriesMax() {
        when(channel.execute(any())).thenReturn(FAILED);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThatThrownBy(response::get).hasCauseInstanceOf(SafeIoException.class);
        verify(channel, times(4)).execute(REQUEST);
    }

    @Test
    public void retriesFirstFailureImmediately() throws Exception {
        when(channel.execute(any())).thenReturn(FAILED).thenReturn(SUCCESS);

        // One retry allows an initial request (not a retry) and a single retry.
        long startTime = System.nanoTime();
        Duration backoffSlotSize = Duration.ofSeconds(10);
        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);

        verify(channel, times(2)).execute(REQUEST);
        assertThat(Duration.ofNanos(System.nanoTime() - startTime))
                .as("First failure with retryable exceptions should be immediately retried")
                .isLessThan(backoffSlotSize);
    }

    @Test
    public void retries_429s() throws Exception {
        when(channel.execute(any())).thenAnswer((Answer<ListenableFuture<Response>>)
                _invocation -> Futures.immediateFuture(new TestResponse().code(429)));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code())
                .as("After retries are exhausted the 429 response should be returned")
                .isEqualTo(429);
        verify(channel, times(4)).execute(REQUEST);
    }

    @Test
    public void retries_429s_dueTo_custom() throws Exception {
        when(channel.execute(any())).thenAnswer((Answer<ListenableFuture<Response>>) _invocation -> {
            TestResponse stubResponse = new TestResponse().code(429);
            QosReasons.encodeToResponse(
                    QosReason.builder().reason("reason").dueTo(DueTo.CUSTOM).build(),
                    stubResponse,
                    TestResponseQosEncoder.INSTANCE);
            return Futures.immediateFuture(stubResponse);
        });

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code())
                .as("After retries are exhausted the 429 response should be returned")
                .isEqualTo(429);
        verify(channel, times(4)).execute(REQUEST);
    }

    @Test
    public void does_not_retry_429_when_hinted() throws Exception {
        TestResponse stubResponse = new TestResponse().code(429);
        QosReasons.encodeToResponse(
                QosReason.builder()
                        .reason("reason")
                        .retryHint(RetryHint.DO_NOT_RETRY)
                        .build(),
                stubResponse,
                TestResponseQosEncoder.INSTANCE);
        when(channel.execute(any())).thenReturn(Futures.immediateFuture(stubResponse));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get())
                .as("The 429 response should be returned without retrying due to RetryHint.DO_NOT_RETRY")
                .isSameAs(stubResponse);
        verify(channel, times(1)).execute(REQUEST);
    }

    @Test
    public void retries_503s() throws Exception {
        when(channel.execute(any())).thenAnswer((Answer<ListenableFuture<Response>>)
                _invocation -> Futures.immediateFuture(new TestResponse().code(503)));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code())
                .as("After retries are exhausted the 503 response should be returned")
                .isEqualTo(503);
        verify(channel, times(4)).execute(REQUEST);
    }

    @Test
    public void retries_503s_dueTo_custom() throws Exception {
        when(channel.execute(any())).thenAnswer((Answer<ListenableFuture<Response>>) _invocation -> {
            TestResponse stubResponse = new TestResponse().code(503);
            QosReasons.encodeToResponse(
                    QosReason.builder().reason("reason").dueTo(DueTo.CUSTOM).build(),
                    stubResponse,
                    TestResponseQosEncoder.INSTANCE);
            return Futures.immediateFuture(stubResponse);
        });

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code())
                .as("After retries are exhausted the 503 response should be returned")
                .isEqualTo(503);
        verify(channel, times(4)).execute(REQUEST);
    }

    @Test
    public void does_not_retry_503_when_hinted() throws Exception {
        TestResponse stubResponse = new TestResponse().code(503);
        QosReasons.encodeToResponse(
                QosReason.builder()
                        .reason("reason")
                        .retryHint(RetryHint.DO_NOT_RETRY)
                        .build(),
                stubResponse,
                TestResponseQosEncoder.INSTANCE);
        when(channel.execute(any())).thenReturn(Futures.immediateFuture(stubResponse));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get())
                .as("The 503 response should be returned without retrying due to RetryHint.DO_NOT_RETRY")
                .isSameAs(stubResponse);
        verify(channel, times(1)).execute(REQUEST);
    }

    @Test
    public void retries_308s() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(308);
        when(mockResponse.getFirstHeader(eq("Location"))).thenReturn(Optional.of("https://localhost"));
        when(channel.execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        long startTime = System.nanoTime();
        Duration backoffSlotSize = Duration.ofSeconds(10);
        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                backoffSlotSize,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get())
                .as("After retries are exhausted the 308 response should be returned")
                .isSameAs(mockResponse);
        verify(channel, times(4)).execute(REQUEST);
        assertThat(Duration.ofNanos(System.nanoTime() - startTime))
                .as("308 responses should be immediately retried")
                .isLessThan(backoffSlotSize);
    }

    @Test
    public void retries_308s_when_429_and_503_are_propagated() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(308);
        when(mockResponse.getFirstHeader(eq("Location"))).thenReturn(Optional.of("https://localhost"));
        when(channel.execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                // This does not apply to 308 responses
                ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get())
                .as("After retries are exhausted the 308 response should be returned")
                .isSameAs(mockResponse);
        verify(channel, times(4)).execute(REQUEST);
    }

    @Test
    public void does_not_retry_308_without_location() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(308);
        when(channel.execute(any())).thenReturn(Futures.immediateFuture(mockResponse));
        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ofSeconds(1),
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get()).isSameAs(mockResponse);
        verify(channel, times(1)).execute(REQUEST);
    }

    @Test
    public void propagates_429s_when_requested() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(429);
        when(channel.execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(429);
        verify(channel, times(1)).execute(REQUEST);
    }

    @Test
    public void retries_500s_when_method_is_safe_and_idempotent() throws Exception {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(500)))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(200)));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.GET,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(200);
        verify(channel, times(2)).execute(REQUEST);
    }

    @Test
    public void retries_500s_when_method_is_safe_and_idempotent_when_qos_propagated() throws Exception {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(500)))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(200)));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.GET,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(200);
        verify(channel, times(2)).execute(REQUEST);
    }

    @Test
    public void retries_500s_for_put() throws Exception {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(500)))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(200)));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.PUT,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(200);
        verify(channel, times(2)).execute(REQUEST);
    }

    @Test
    public void retries_500s_for_delete() throws Exception {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(500)))
                .thenReturn(Futures.immediateFuture(new TestResponse().code(200)));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.DELETE,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(200);
        verify(channel, times(2)).execute(REQUEST);
    }

    @Test
    public void doesnt_retry_500s_for_post() throws Exception {
        when(channel.execute(any())).thenReturn(Futures.immediateFuture(new TestResponse().code(500)));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(500);
        verify(channel, times(1)).execute(REQUEST);
    }

    @Test
    public void returns_503s_when_requested() throws Exception {
        Response mockResponse = mock(Response.class);
        when(mockResponse.code()).thenReturn(503);
        when(channel.execute(any())).thenReturn(Futures.immediateFuture(mockResponse));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code()).isEqualTo(503);
        verify(channel, times(1)).execute(REQUEST);
    }

    @Test
    public void response_bodies_are_closed() throws Exception {
        Response response1 = mockResponse(503);
        Response response2 = mockResponse(503);
        Response eventualSuccess = mockResponse(200);

        when(channel.execute(any()))
                .thenReturn(Futures.immediateFuture(response1))
                .thenReturn(Futures.immediateFuture(response2))
                .thenReturn(Futures.immediateFuture(eventualSuccess));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get(1, TimeUnit.SECONDS).code()).isEqualTo(200);

        verify(response1, times(1)).close();
        verify(response2, times(1)).close();
    }

    @Test
    public void final_exhausted_failure_response_body_is_not_closed() throws Exception {
        TestResponse response1 = new TestResponse().code(503);
        TestResponse response2 = new TestResponse().code(503);
        TestResponse response3 = new TestResponse().code(503);

        when(channel.execute(any()))
                .thenReturn(Futures.immediateFuture(response1))
                .thenReturn(Futures.immediateFuture(response2))
                .thenReturn(Futures.immediateFuture(response3));

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                2,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
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
        when(channel.execute(any())).thenReturn(delegateResult);
        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                3,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> retryingResult = retryer.execute(REQUEST);
        assertThat(retryingResult.cancel(true)).isTrue();
        assertThat(delegateResult).as("Failed to cancel the delegate future").isCancelled();
    }

    private static SocketException createEtimedoutException() {
        // Message must precisely match:
        // https://github.com/openjdk/jdk/blob/32eb5290c207d5fda398ee09b354b8cf55b89e0c/src/hotspot/share/runtime/os.cpp#L1658
        return new SocketException("Connection timed out");
    }

    @Test
    public void doesNotRetryEtimedoutSocketException() {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFailedFuture(createEtimedoutException()))
                .thenReturn(SUCCESS);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThatThrownBy(response::get)
                .hasRootCauseExactlyInstanceOf(SocketException.class)
                .hasRootCauseMessage("Connection timed out");
    }

    @Test
    public void doesNotRetrySocketTimeout() {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFailedFuture(new SocketTimeoutException()))
                .thenReturn(SUCCESS);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThatThrownBy(response::get).hasRootCauseExactlyInstanceOf(SocketTimeoutException.class);
    }

    @Test
    public void retriesSocketTimeoutWhenRequested() throws ExecutionException, InterruptedException {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFailedFuture(new SocketTimeoutException()))
                .thenReturn(SUCCESS);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DANGEROUS_ENABLE_AT_RISK_OF_RETRY_STORMS);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void retriesEtimedoutWhenRequested() throws ExecutionException, InterruptedException {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFailedFuture(createEtimedoutException()))
                .thenReturn(SUCCESS);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DANGEROUS_ENABLE_AT_RISK_OF_RETRY_STORMS);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void retriesEtimedoutWithAlternativeExceptionType() throws ExecutionException, InterruptedException {
        SocketException etimedoutException = createEtimedoutException();
        SocketException subtype = new ConnectException(etimedoutException.getMessage());
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFailedFuture(subtype))
                .thenReturn(SUCCESS);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void doesNotRetryRuntimeException() {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFailedFuture(new SafeRuntimeException("bug")))
                .thenReturn(SUCCESS);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThatThrownBy(response::get)
                .hasRootCauseExactlyInstanceOf(SafeRuntimeException.class)
                .hasRootCauseMessage("bug");
    }

    @Test
    public void retriesSocketTimeout_connectionTimeout() throws ExecutionException, InterruptedException {
        when(channel.execute(any()))
                // Magic string allows us to retry on RetryOnTimeout.DISABLED
                .thenReturn(Futures.immediateFailedFuture(new SocketTimeoutException("connect timed out")))
                .thenReturn(SUCCESS);

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get()).isEqualTo(EXPECTED_RESPONSE);
    }

    @Test
    public void requestWithNonRepeatableBodyRetriedWhenConnectionFails()
            throws ExecutionException, InterruptedException {
        when(channel.execute(any())).thenReturn(FAILED).thenReturn(SUCCESS);

        // One retry allows an initial request (not a retry) and a single retry.
        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                1,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(Request.builder()
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
        assertThat(response.get())
                .as("requests should be retried if they are not consumed")
                .isEqualTo(EXPECTED_RESPONSE);
        verify(channel, times(2)).execute(any());
    }

    @Test
    public void requestWithNonRepeatableBodyNotRetriedWhenBodyIsConsumed() {
        when(channel.execute(any())).thenAnswer((Answer<ListenableFuture<Response>>) invocation -> {
            Request request = invocation.getArgument(0);
            assertThat(request.body()).isPresent();

            // Consume the message.
            request.body().get().writeTo(new ByteArrayOutputStream());
            return FAILED;
        });

        EndpointChannel retryer = new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                2,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
        ListenableFuture<Response> response = retryer.execute(Request.builder()
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
        assertThatThrownBy(response::get)
                .as("requests should not be retried if they are consumed")
                .hasRootCauseExactlyInstanceOf(SafeIoException.class)
                .hasRootCauseMessage("FAILED");
        verify(channel, times(1)).execute(any());
    }

    @Test
    public void retryCountSuccessHistogramUpdatedAfter429ThenSuccess() throws Exception {
        // 2 retries that return 429 before a successful response
        setupResponses(429, 429, 200);

        EndpointChannel retryer = channel(4);

        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get().code()).isEqualTo(200);
        verify(channel, times(3)).execute(REQUEST);

        verifyMetrics(
                new ExpectedMetrics(RequestRetryCount_Result.SUCCESS, 2),
                "2 retries with request eventually succeeding after 429s");
    }

    @Test
    public void retryCountFailureHistogramUpdatedWhenRetriesExhaustedDueToException() {
        when(channel.execute(any())).thenReturn(FAILED);

        EndpointChannel retryer = channel(4);

        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThatThrownBy(response::get).hasCauseInstanceOf(SafeIoException.class);
        verify(channel, times(5)).execute(REQUEST);

        verifyMetrics(
                new ExpectedMetrics(RequestRetryCount_Result.FAILURE, 5, "SafeIoException"),
                "retries exhausted due to exception");
    }

    @Test
    public void retryCountFailureHistogramUpdatedWhenRetriesExhaustedDueTo429() throws Exception {
        setupResponses(429);

        EndpointChannel retryer = channel(4);

        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get().code()).isEqualTo(429);
        verify(channel, times(5)).execute(REQUEST);

        // Exhausted 429s are not successful responses, so they update the FAILURE histogram
        verifyMetrics(
                new ExpectedMetrics(RequestRetryCount_Result.FAILURE, 5, "qosResponse"),
                "retries exhausted due to 429 response");
    }

    @Test
    public void testIoExceptionFailuresThenQosResponseThenSuccess() throws Exception {
        when(channel.execute(any()))
                .thenReturn(Futures.immediateFailedFuture(new SocketTimeoutException("connect timed out")))
                .thenReturn(FAILED)
                .thenAnswer((Answer<ListenableFuture<Response>>)
                        _invocation -> Futures.immediateFuture(new TestResponse().code(429)))
                .thenAnswer((Answer<ListenableFuture<Response>>)
                        _invocation -> Futures.immediateFuture(new TestResponse().code(200)));

        EndpointChannel retryer = channel(4);

        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response).isDone();
        assertThat(response.get().code())
                .as("Should succeed after IOException failures followed by a retryable 429")
                .isEqualTo(200);
        verify(channel, times(4)).execute(REQUEST);

        verifyMetrics(
                new ExpectedMetrics(RequestRetryCount_Result.SUCCESS, 3),
                "2 IOExceptions and 1 retryable 429 before success");
    }

    @Test
    public void retryCountNonRetryableHistogramUpdatedWhenNonRetryableResponseAfterRetries() throws Exception {
        setupResponses(429, 400);

        EndpointChannel retryer = channel(4);

        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get().code()).isEqualTo(400);
        verify(channel, times(2)).execute(REQUEST);

        verifyMetrics(
                new ExpectedMetrics(RequestRetryCount_Result.NON_RETRYABLE, 1),
                "non-retryable response after retryable 429");
    }

    @Test
    public void retryCountNonRetryableHistogramUpdatedWhenNonRetryableResponseAfterAlmostMaxRetries() throws Exception {
        setupResponses(429, 429, 429, 429, 400);

        EndpointChannel retryer = channel(4);

        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get().code()).isEqualTo(400);
        verify(channel, times(5)).execute(REQUEST);

        verifyMetrics(
                // We should update the non-retryable metric if we get a non-retryable response at the last retry
                new ExpectedMetrics(RequestRetryCount_Result.NON_RETRYABLE, 4),
                "non-retryable response after retryable 429");
    }

    @Test
    public void retryCountSuccessHistogramUpdatedForFirstTrySuccess() throws Exception {
        setupResponses(200);

        EndpointChannel retryer = channel(4);

        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get().code()).isEqualTo(200);

        verifyMetrics(new ExpectedMetrics(RequestRetryCount_Result.SUCCESS, 0), "first-try success without retries");
    }

    @Test
    public void retryCountUpdatedForNon2xxFirstTryResponse() throws Exception {
        setupResponses(400);

        EndpointChannel retryer = channel(4);

        ListenableFuture<Response> response = retryer.execute(REQUEST);
        assertThat(response.get().code()).isEqualTo(400);
        verify(channel, times(1)).execute(REQUEST);

        verifyMetrics(new ExpectedMetrics(RequestRetryCount_Result.NON_RETRYABLE, 0), "non-2xx first-try response");
    }

    private EndpointChannel channel(int maxRetries) {
        return new RetryingChannel(
                channel,
                TestEndpoint.POST,
                "my-channel",
                registry,
                maxRetries,
                Duration.ZERO,
                ClientConfiguration.ServerQoS.AUTOMATIC_RETRY,
                ClientConfiguration.RetryOnTimeout.DISABLED);
    }

    private void setupResponses(int... codes) {
        OngoingStubbing<ListenableFuture<Response>> stubbing = when(channel.execute(any()));
        for (int code : codes) {
            stubbing = stubbing.thenAnswer(_invocation -> Futures.immediateFuture(new TestResponse().code(code)));
        }
    }

    private record ExpectedMetrics(
            RequestRetryCount_Result resultState, long retryCount, Optional<String> exhaustedReason) {
        ExpectedMetrics(RequestRetryCount_Result resultState, long retryCount) {
            this(resultState, retryCount, Optional.empty());
        }

        ExpectedMetrics(RequestRetryCount_Result resultState, long retryCount, String exhaustedReason) {
            this(resultState, retryCount, Optional.of(exhaustedReason));
        }
    }

    private void verifyMetrics(ExpectedMetrics expected, String description) {
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        assertThat(metrics.requestRetryCount()
                .channelName("my-channel")
                .result(expected.resultState)
                .build()
                .getCount())
                .as(expected.resultState + " histogram should be updated (" + description + ")")
                .isEqualTo(1);
        for (RequestRetryCount_Result result : RequestRetryCount_Result.values()) {
            if (result != expected.resultState) {
                assertThat(metrics.requestRetryCount()
                        .channelName("my-channel")
                        .result(result)
                        .build()
                        .getCount())
                        .as(result + " histogram should not be updated (" + description + ")")
                        .isEqualTo(0);
            } else {
                assertThat(metrics.requestRetryCount()
                        .channelName("my-channel")
                        .result(result)
                        .build()
                        .getSnapshot()
                        .getValues())
                        .as(expected.resultState + " histogram should record " + expected.retryCount + " retry ("
                                + description + ")")
                        .containsExactly(expected.retryCount);
            }
        }
        if (expected.exhaustedReason.isPresent()) {
            assertThat(metrics.requestRetryExhausted()
                    .channelName("my-channel")
                    .reason(expected.exhaustedReason.get())
                    .build()
                    .getCount())
                    .as("Exhausted counter should be incremented with reason " + expected.exhaustedReason.get() + " ("
                            + description + ")")
                    .isEqualTo(1);
        } else {
            // Ideally we'd check we didn't increment the exhausted counter for any reason, but we don't really
            //   have a way to query all possible reasons, so just check the predefined one and the exception reason
            //   that we use in the tests.
            for (String reason : List.of("qosResponse", "serverError", "SafeIoException")) {
                assertThat(metrics.requestRetryExhausted()
                        .channelName("my-channel")
                        .reason(reason)
                        .build()
                        .getCount())
                        .as("Exhausted counter should not be incremented with reason " + reason + " (" + description
                                + ")")
                        .isEqualTo(0);
            }
        }
    }

    private static Response mockResponse(int status) {
        Response response = mock(Response.class);
        when(response.code()).thenReturn(status);
        return response;
    }
}
