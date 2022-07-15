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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.TestTracing;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public final class DialogueChannelTest {

    public static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("foo", "1.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"), Paths.get("src/test/resources/keyStore.jks"), "keystore");
    private static final ClientConfiguration stubConfig = ClientConfiguration.builder()
            .from(ClientConfigurations.of(ServiceConfiguration.builder()
                    .addUris("http://localhost")
                    .security(SSL_CONFIG)
                    .build()))
            .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
            .userAgent(USER_AGENT)
            .build();

    @Mock
    private Channel mockChannel;

    private Endpoint endpoint = TestEndpoint.POST;

    @Mock
    private Response response;

    private Request request = Request.builder().build();
    private DialogueChannel channel;

    @BeforeEach
    public void before() {
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> mockChannel)
                .build();

        ListenableFuture<Response> expectedResponse = Futures.immediateFuture(response);
        lenient().when(mockChannel.execute(eq(endpoint), any())).thenReturn(expectedResponse);
    }

    @Test
    public void testRequestMakesItThrough() throws ExecutionException, InterruptedException {
        assertThat(channel.execute(endpoint, request).get()).isNotNull();
    }

    @Test
    public void bad_channel_throwing_an_exception_still_returns_a_future() {
        Channel badUserImplementation = new Channel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
                throw new IllegalStateException("Always throw");
            }
        };

        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> badUserImplementation)
                .build();

        // this should never throw
        ListenableFuture<Response> future = channel.execute(endpoint, request);

        // only when we access things do we allow exceptions
        assertThatThrownBy(() -> Futures.getUnchecked(future)).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    public void bad_channel_throwing_an_error_still_returns_a_future() {
        Channel badUserImplementation = new Channel() {
            @Override
            public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
                throw new NoClassDefFoundError("something is broken");
            }
        };

        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> badUserImplementation)
                .build();

        // this should never throw
        ListenableFuture<Response> future = channel.execute(endpoint, request);

        // only when we access things do we allow exceptions
        assertThatThrownBy(() -> Futures.getUnchecked(future)).hasCauseInstanceOf(NoClassDefFoundError.class);
    }

    @Test
    public void when_thread_is_interrupted_no_calls_to_delegate() {
        Channel delegate = mock(Channel.class);

        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> delegate)
                .build();

        Thread.currentThread().interrupt();
        ListenableFuture<Response> future = channel.execute(endpoint, request);

        assertThat(future).isDone();
        assertThat(future).isNotCancelled();
        verifyNoInteractions(delegate);
    }

    @Test
    void test_queue_rejection_is_not_retried() {
        when(mockChannel.execute(any(), any())).thenReturn(SettableFuture.create());
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> mockChannel)
                .random(new Random(123456L))
                .maxQueueSize(1)
                .build();
        // Saturate the concurrency limiter
        int initialConcurrencyLimit = 20;
        for (int i = 0; i < initialConcurrencyLimit; i++) {
            ListenableFuture<Response> running = channel.execute(endpoint, request);
            assertThat(running).isNotDone();
        }
        // Queue a request
        ListenableFuture<Response> queued = channel.execute(endpoint, request);
        assertThat(queued).isNotDone();
        // Next request should be rejected.
        ListenableFuture<Response> rejected = channel.execute(endpoint, request);
        assertThat(rejected).isDone();
        assertThatThrownBy(rejected::get)
                .hasRootCauseExactlyInstanceOf(SafeRuntimeException.class)
                .hasMessageContaining("queue is full");
    }

    static class ThrowingOutputStream extends OutputStream {
        private final Supplier<IOException> supplier;

        ThrowingOutputStream(Supplier<IOException> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void write(int _byte) throws IOException {
            throw supplier.get();
        }

        @Override
        public void write(byte[] _buf, int _off, int _len) throws IOException {
            throw supplier.get();
        }
    }

    private ListenableFuture<Response> makeStructuredRequestToClosedConnection(Supplier<IOException> failure) {
        AtomicInteger channelInteractions = new AtomicInteger();
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> (_endpoint, currentRequest) -> {
                    int interactions = channelInteractions.incrementAndGet();
                    if (interactions > 1) {
                        return Futures.immediateFuture(new TestResponse()
                                .code(204)
                                .withHeader("Interactions", Integer.toString(interactions)));
                    }
                    Optional<RequestBody> body = currentRequest.body();
                    assertThat(body).isPresent();
                    try {
                        body.get().writeTo(new ThrowingOutputStream(failure));
                    } catch (IOException e) {
                        return Futures.immediateFailedFuture(e);
                    }
                    throw new AssertionError("Expected an exception");
                })
                .random(new Random(123456L))
                .maxQueueSize(1)
                .build();
        RequestBody defaultStructuredBody = DefaultConjureRuntime.builder()
                .build()
                .bodySerDe()
                .serializer(new TypeMarker<List<String>>() {})
                .serialize(ImmutableList.of("test"));
        return channel.execute(
                endpoint, Request.builder().body(defaultStructuredBody).build());
    }

    @Test
    void test_serialization_socket_error_is_retried() {
        ListenableFuture<Response> result =
                makeStructuredRequestToClosedConnection(() -> new SocketException("broken pipe"));
        assertThat(result).succeedsWithin(Duration.ofSeconds(2)).satisfies(res -> {
            assertThat(res.code()).isEqualTo(204);
            assertThat(res.getFirstHeader("Interactions"))
                    .as("Expected retries")
                    .hasValue("2");
        });
    }

    @Test
    void test_serialization_socket_timeout_is_not_retried() {
        ListenableFuture<Response> result =
                makeStructuredRequestToClosedConnection(() -> new SocketTimeoutException("oops"));
        assertThat(result).as("Socket timeouts should not be retried").failsWithin(Duration.ofSeconds(2));
    }

    @Test
    void constructing_a_client_with_zero_uris_causes_immediate_failures() {
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(ClientConfiguration.builder()
                        .from(stubConfig)
                        .uris(Collections.emptyList())
                        .build())
                .factory(_args -> mockChannel)
                .build();
        ListenableFuture<Response> future = channel.execute(endpoint, request);
        assertThatThrownBy(future::get).hasRootCauseInstanceOf(SafeIllegalStateException.class);
    }

    @Test
    void nice_tostring() {
        DialogueChannelFactory factory = _args -> mockChannel;
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(factory)
                .build();
        DialogueChannel channel2 = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(factory)
                .build();
        System.out.println(channel);
        assertThat(channel.toString())
                .describedAs("It's important we can differentiate two instances built from the same config!")
                .isNotEqualTo(channel2.toString());
    }

    @ParameterizedTest
    @EnumSource(NodeSelectionStrategy.class)
    public void test_can_request_sticky_token_with_single_uri(NodeSelectionStrategy nodeSelectionStrategy)
            throws ExecutionException {
        test_can_use_sticky_attachments_impl(nodeSelectionStrategy, ImmutableList.of("http://localhost"));
    }

    @ParameterizedTest
    @EnumSource(NodeSelectionStrategy.class)
    public void test_can_request_sticky_token_with_many_uris(NodeSelectionStrategy nodeSelectionStrategy)
            throws ExecutionException {
        test_can_use_sticky_attachments_impl(
                nodeSelectionStrategy,
                ImmutableList.of("http://localhost", "http" + "://localhost2", "http" + "://localhost3"));
    }

    private void test_can_use_sticky_attachments_impl(
            NodeSelectionStrategy nodeSelectionStrategy, ImmutableList<String> uris) throws ExecutionException {
        String uriHeader = "uri";
        DialogueChannelFactory factory = args -> {
            mockChannel = Mockito.mock(Channel.class);
            lenient().when(mockChannel.execute(eq(endpoint), any())).thenAnswer((Answer<ListenableFuture<Response>>)
                    _invocation ->
                            Futures.immediateFuture(TestResponse.withBody(null).withHeader(uriHeader, args.uri())));
            return mockChannel;
        };
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(ClientConfiguration.builder()
                        .uris(uris)
                        .from(stubConfig)
                        .nodeSelectionStrategy(nodeSelectionStrategy)
                        .build())
                .factory(factory)
                .random(new Random(1L))
                .build();

        request = createRequestWithAddExecutedOnAttachment();
        response = Futures.getDone(channel.execute(endpoint, request));
        String expectedUri = response.getFirstHeader(uriHeader).get();

        Consumer<Request> stickyTarget = StickyAttachments.copyStickyTarget(response);

        request = Request.builder().build();
        stickyTarget.accept(request);
        response = Futures.getDone(channel.execute(endpoint, request));
        assertThat(response.getFirstHeader(uriHeader)).hasValue(expectedUri);
    }

    private Request createRequestWithAddExecutedOnAttachment() {
        Request newRequest = Request.builder().build();
        StickyAttachments.requestStickyToken(newRequest);
        return newRequest;
    }

    @Test
    @TestTracing(snapshot = true)
    public void traces_on_retries() throws Exception {
        when(response.code()).thenReturn(429);
        try (Response response = channel.execute(endpoint, request).get()) {
            assertThat(response.code()).isEqualTo(429);
        }
    }

    @Test
    @TestTracing(snapshot = true)
    public void traces_on_success() throws Exception {
        when(response.code()).thenReturn(200);
        try (Response response = channel.execute(endpoint, request).get()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    public void test_request_gzip_by_default() {
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> (_endpoint, req) -> {
                    // Reflect request headers to the response for easy verification
                    when(response.headers()).thenReturn(req.headerParams());
                    return Futures.immediateFuture(response);
                })
                .build();
        ListenableFuture<Response> future = channel.execute(endpoint, request);
        ListMultimap<String, String> reflectedRequestHeaders =
                Futures.getUnchecked(future).headers();
        String acceptEncoding = Iterables.getOnlyElement(reflectedRequestHeaders.get("Accept-Encoding"));
        assertThat(acceptEncoding).isEqualTo("gzip");
    }

    @Test
    public void test_accepts_identity_with_range() {
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> (_endpoint, req) -> {
                    // Reflect request headers to the response for easy verification
                    when(response.headers()).thenReturn(req.headerParams());
                    return Futures.immediateFuture(response);
                })
                .build();
        ListenableFuture<Response> future = channel.execute(
                endpoint,
                Request.builder().putHeaderParams("Range", "bytes 1-3").build());
        ListMultimap<String, String> reflectedRequestHeaders =
                Futures.getUnchecked(future).headers();
        String acceptEncoding = Iterables.getOnlyElement(reflectedRequestHeaders.get("Accept-Encoding"));
        assertThat(acceptEncoding).isEqualTo("identity");
        String requestRange = Iterables.getOnlyElement(reflectedRequestHeaders.get("Range"));
        assertThat(requestRange).isEqualTo("bytes 1-3");
    }

    @Test
    public void test_allows_custom_accept_with_range_request() {
        channel = DialogueChannel.builder()
                .channelName("my-channel")
                .clientConfiguration(stubConfig)
                .factory(_args -> (_endpoint, req) -> {
                    // Reflect request headers to the response for easy verification
                    when(response.headers()).thenReturn(req.headerParams());
                    return Futures.immediateFuture(response);
                })
                .build();
        ListenableFuture<Response> future = channel.execute(
                endpoint,
                Request.builder()
                        .putHeaderParams("Range", "bytes 1-3")
                        .putHeaderParams("Accept-Encoding", "foo")
                        .build());
        ListMultimap<String, String> reflectedRequestHeaders =
                Futures.getUnchecked(future).headers();
        String acceptEncoding = Iterables.getOnlyElement(reflectedRequestHeaders.get("Accept-Encoding"));
        assertThat(acceptEncoding).isEqualTo("foo");
        String requestRange = Iterables.getOnlyElement(reflectedRequestHeaders.get("Range"));
        assertThat(requestRange).isEqualTo("bytes 1-3");
    }
}
