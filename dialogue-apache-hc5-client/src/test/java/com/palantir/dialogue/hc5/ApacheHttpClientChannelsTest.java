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
package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.google.common.collect.Lists;
import com.google.common.collect.MoreCollectors;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.AbstractChannelTest;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

public final class ApacheHttpClientChannelsTest extends AbstractChannelTest {

    @Override
    protected Channel createChannel(ClientConfiguration config) {
        return ApacheHttpClientChannels.create(config);
    }

    @Test
    public void close_doesnt_fail_inflight_requests() throws Exception {
        ClientConfiguration conf = TestConfigurations.create("http://foo");

        Channel channel;
        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, "client")) {

            channel = ApacheHttpClientChannels.createSingleUri("http://foo", client);
            ListenableFuture<Response> response =
                    channel.execute(TestEndpoint.POST, Request.builder().build());
            assertThatThrownBy(() -> Futures.getUnchecked(response))
                    .getCause()
                    .isInstanceOfSatisfying(UnknownHostException.class, throwable -> assertThat(
                                    throwable.getSuppressed()[0])
                            .satisfies(diagnosticThrowable -> assertThat(diagnosticThrowable.getStackTrace())
                                    .as("Diagnostic exception should have an empty stack trace")
                                    .isEmpty())
                            .isInstanceOfSatisfying(SafeLoggable.class, safeLoggable -> {
                                assertThat(Lists.transform(safeLoggable.getArgs(), Arg::getName))
                                        .as("Expected a diagnostic exception")
                                        .containsExactlyInAnyOrder(
                                                "durationMillis",
                                                "connectTimeout",
                                                "socketTimeout",
                                                "clientName",
                                                "serviceName",
                                                "endpointName",
                                                "requestTraceId",
                                                "requestSpanId",
                                                "hostIndex");
                            }));
        }

        ListenableFuture<Response> again =
                channel.execute(TestEndpoint.POST, Request.builder().build());
        assertThatThrownBy(() -> Futures.getUnchecked(again)).hasCauseInstanceOf(UnknownHostException.class);
    }

    @Test
    public void metrics() throws Exception {
        ClientConfiguration conf = TestConfigurations.create("http://unused");

        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, "testClient")) {

            Channel channel = ApacheHttpClientChannels.createSingleUri("http://neverssl.com", client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());

            TaggedMetricRegistry metrics = conf.taggedMetricRegistry();
            try (Response response = Futures.getUnchecked(future)) {
                assertThat(response.code()).isEqualTo(200);

                assertThat(poolGaugeValue(metrics, "testClient", "idle"))
                        .describedAs("available")
                        .isZero();
                assertThat(poolGaugeValue(metrics, "testClient", "leased"))
                        .describedAs("leased")
                        .isEqualTo(1L);
            }

            assertThat(poolGaugeValue(metrics, "testClient", "idle"))
                    .describedAs("available after response closed")
                    .isOne();
            assertThat(poolGaugeValue(metrics, "testClient", "leased"))
                    .describedAs("leased after response closed")
                    .isZero();
        }
    }

    @Test
    public void countsUnknownHostExceptions() throws Exception {
        ClientConfiguration conf = TestConfigurations.create("http://unused");

        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, "testClient")) {

            Meter connectionResolutionError =
                    DialogueClientMetrics.of(conf.taggedMetricRegistry()).connectionResolutionError("testClient");

            assertThat(connectionResolutionError.getCount()).isZero();

            Channel channel =
                    ApacheHttpClientChannels.createSingleUri("http://unknown-host-for-testing.unused", client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());

            try (Response response = Futures.getUnchecked(future)) {
                fail("This request should have failed with an unknown host exception! (code: %d)", response.code());
            } catch (UncheckedExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(UnknownHostException.class);
            }

            assertThat(connectionResolutionError.getCount()).isEqualTo(1L);
        }
    }

    @Test
    public void countsConnectErrors() throws Exception {
        ClientConfiguration conf = ClientConfiguration.builder()
                .from(TestConfigurations.create("http://unused"))
                .connectTimeout(Duration.ofMillis(1))
                .build();

        try (ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(conf, "testClient")) {

            Meter connectionCreateError = DialogueClientMetrics.of(conf.taggedMetricRegistry())
                    .connectionCreateError()
                    .clientName("testClient")
                    .cause("ConnectTimeoutException")
                    .build();

            assertThat(connectionCreateError.getCount()).isZero();

            // 203.0.113.0/24 is a test network that should never exist
            Channel channel = ApacheHttpClientChannels.createSingleUri("http://203.0.113.23", client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());

            try (Response response = Futures.getUnchecked(future)) {
                fail("This request should have failed with an connection timeout! (code: %d)", response.code());
            } catch (UncheckedExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(SafeConnectTimeoutException.class);
            }

            assertThat(connectionCreateError.getCount()).isEqualTo(1L);
        }
    }

    @Test
    public void supportsContentLengthHeader() {
        testContentLength(
                Optional.of(Integer.toString(CONTENT.length)),
                Optional.of(new RequestBody() {
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
                        return OptionalLong.empty();
                    }

                    @Override
                    public void close() {}
                }),
                CONTENT.length);
    }

    @Test
    public void supportsContentLengthValueOnBody() {
        testContentLength(Optional.empty(), Optional.of(body), CONTENT.length);
    }

    @Test
    public void contentLengthHeaderPreferredToBody() {
        int fakeLength = CONTENT.length - 1;
        testContentLength(
                Optional.of(Integer.toString(CONTENT.length)),
                Optional.of(new RequestBody() {
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
                        return OptionalLong.of(fakeLength);
                    }

                    @Override
                    public void close() {}
                }),
                CONTENT.length);
    }

    @Test
    public void unparseableContentLengthHeaderIsSupported() {
        testContentLength(Optional.of("you can't parse me!"), Optional.of(body), CONTENT.length);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void testContentLength(Optional<String> headerValue, Optional<RequestBody> body, long value) {
        try {
            endpoint.method = HttpMethod.POST;
            Request.Builder builder = Request.builder().from(request).body(body);
            headerValue.ifPresent(header -> builder.putHeaderParams(HttpHeaders.CONTENT_LENGTH, header));
            request = builder.build();
            channel.execute(endpoint, request);
            RecordedRequest recordedRequest = server.takeRequest();
            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
            assertThat(recordedRequest.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo(Long.toString(value));
            assertThat(recordedRequest.getHeader(HttpHeaders.TRANSFER_ENCODING)).isNull();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("JdkObsolete")
    private long poolGaugeValue(TaggedMetricRegistry metrics, String clientName, String state) {
        Metric gauge = metrics.getMetrics().entrySet().stream()
                .filter(entry -> entry.getKey().safeName().equals("dialogue.client.pool.size"))
                .filter(entry -> clientName.equals(entry.getKey().safeTags().get("client-name")))
                .filter(entry -> state.equals(entry.getKey().safeTags().get("state")))
                .map(Map.Entry::getValue)
                .collect(MoreCollectors.onlyElement());
        assertThat(gauge).isInstanceOf(Gauge.class);
        Object value = ((Gauge<?>) gauge).getValue();
        assertThat(value).isInstanceOf(Long.class);
        return (long) value;
    }
}
