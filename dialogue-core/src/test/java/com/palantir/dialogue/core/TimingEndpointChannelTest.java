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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.util.concurrent.Futures;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.ResponseAttachments;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.logsafe.Preconditions;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("FutureReturnValueIgnored")
public final class TimingEndpointChannelTest {

    @Mock
    private EndpointChannel delegate;

    @Mock
    private Ticker ticker;

    @Test
    public void addsMetricsForSuccessfulResponses() {
        testThat().successfulResponseWithCode(200).isCountedAsSuccess();
        testThat().successfulResponseWithCode(204).isCountedAsSuccess();
    }

    @Test
    public void addsMetricsForClientErrorResponses() {
        testThat().successfulResponseWithCode(403).isIgnored();
        testThat().successfulResponseWithCode(404).isIgnored();
    }

    @Test
    public void addsMetricsForQosResponses() {
        testThat()
                .successfulResponseWithCode(308)
                .withHeader("Location", "https://localhost")
                .isCountedAsFailure();
        testThat().successfulResponseWithCode(429).isCountedAsFailure();
        testThat().successfulResponseWithCode(503).isCountedAsFailure();
    }

    @Test
    public void addsMetricsForServerErrors() {
        testThat().successfulResponseWithCode(500).isCountedAsFailure();
    }

    @Test
    public void addsMetricsForIoExceptions() {
        testThat().failedResponse(new UnknownHostException()).isCountedAsFailure();
        testThat().failedResponse(new IOException()).isCountedAsFailure();
        testThat().failedResponse(new ConnectException()).isCountedAsFailure();
        testThat().failedResponse(new SSLHandshakeException("oops")).isCountedAsFailure();
    }

    @Test
    public void addsMetricsForRuntimeExceptions() {
        testThat().failedResponse(new RuntimeException()).isIgnored();
    }

    private TestCase testThat() {
        return new TestCase();
    }

    private final class TestCase {

        private final TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        private final Endpoint endpoint = TestEndpoint.POST;
        private final Timer success = ClientMetrics.of(registry)
                .response()
                .channelName("my-channel")
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .status("success")
                .build();
        private final Timer failure = ClientMetrics.of(registry)
                .response()
                .channelName("my-channel")
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .status("failure")
                .build();

        private OptionalInt maybeCode = OptionalInt.empty();
        private Optional<Throwable> maybeThrowable = Optional.empty();

        private Map<String, String> headers = new HashMap<>();

        @CheckReturnValue
        TestCase successfulResponseWithCode(int code) {
            this.maybeCode = OptionalInt.of(code);
            return this;
        }

        @CheckReturnValue
        TestCase withHeader(String headerName, String headerValue) {
            headers.put(headerName, headerValue);
            return this;
        }

        @CheckReturnValue
        TestCase failedResponse(Throwable throwable) {
            this.maybeThrowable = Optional.of(throwable);
            return this;
        }

        void isCountedAsSuccess() {
            assertMetrics(1, 0);
        }

        void isCountedAsFailure() {
            assertMetrics(0, 1);
        }

        void isIgnored() {
            assertMetrics(0, 0);
        }

        private void assertMetrics(int successCount, int failureCount) {
            runRequest();
            assertThat(success.getCount()).isEqualTo(successCount);
            assertThat(failure.getCount()).isEqualTo(failureCount);
        }

        private void runRequest() {
            Preconditions.checkState(
                    maybeCode.isPresent() ^ maybeThrowable.isPresent(), "Either code of throwable need to be present");
            maybeCode.ifPresent(code -> {
                Response response = new Response() {
                    final ResponseAttachments attachments = ResponseAttachments.create();

                    @Override
                    public InputStream body() {
                        return new ByteArrayInputStream(new byte[0]);
                    }

                    @Override
                    public int code() {
                        return code;
                    }

                    @Override
                    public ListMultimap<String, String> headers() {
                        ListMultimap<String, String> tmpHeaders = MultimapBuilder.treeKeys(
                                        String.CASE_INSENSITIVE_ORDER)
                                .arrayListValues()
                                .build();
                        headers.forEach(tmpHeaders::put);
                        return tmpHeaders;
                    }

                    @Override
                    public ResponseAttachments attachments() {
                        return attachments;
                    }

                    @Override
                    public void close() {}
                };
                when(delegate.execute(any())).thenReturn(Futures.immediateFuture(response));
            });
            maybeThrowable.ifPresent(throwable -> {
                when(delegate.execute(any())).thenReturn(Futures.immediateFailedFuture(throwable));
            });
            new TimingEndpointChannel(delegate, ticker, registry, "my-channel", endpoint)
                    .execute(Request.builder().build());
        }
    }
}
