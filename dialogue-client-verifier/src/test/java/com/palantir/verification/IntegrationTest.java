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

package com.palantir.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.RateLimiter;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.clients.ResponseSizeTooLargeException;
import com.palantir.dialogue.example.AliasOfAliasOfOptional;
import com.palantir.dialogue.example.AliasOfOptional;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.logsafe.Preconditions;
import com.palantir.refreshable.Refreshable;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.immutables.value.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class IntegrationTest {
    private static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("BinaryReturnTypeTest", "0.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("../dialogue-core/src/test/resources/trustStore.jks"),
            Paths.get("../dialogue-core/src/test/resources/keyStore.jks"),
            "keystore");

    private static final int MAX_RESPONSE_SIZE = 128;

    private Undertow undertow;
    private HttpHandler undertowHandler;
    private SampleServiceBlocking blocking;
    private SampleServiceBlocking blockingMaxResponseSize;
    private SampleServiceAsync async;

    @BeforeEach
    public void before() {
        undertow = Undertow.builder()
                .addHttpListener(
                        0, "localhost", new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();
        ServiceConfiguration config = ServiceConfiguration.builder()
                .addUris(getUri(undertow))
                .security(SSL_CONFIG)
                .readTimeout(Duration.ofSeconds(1))
                .writeTimeout(Duration.ofSeconds(1))
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(ServicesConfigBlock.empty()))
                .withUserAgent(USER_AGENT);

        blocking = factory.getNonReloading(SampleServiceBlocking.class, config);
        async = factory.getNonReloading(SampleServiceAsync.class, config);

        blockingMaxResponseSize =
                factory.withMaxResponseSize(MAX_RESPONSE_SIZE).getNonReloading(SampleServiceBlocking.class, config);
    }

    @Test
    public void alias_of_optional() {
        set204Response();
        AliasOfOptional myAlias = blocking.getMyAlias();
        Optional<String> maybeString = myAlias.get();
        assertThat(maybeString).isNotPresent();
    }

    @Test
    public void alias_of_alias_of_optional() {
        set204Response();
        AliasOfAliasOfOptional myAlias = blocking.getMyAlias2();
        Optional<String> maybeString = myAlias.get().get();
        assertThat(maybeString).isNotPresent();
    }

    @Test
    public void conjure_generated_async_interface_with_optional_binary_return_type_and_gzip() {
        setBinaryGzipResponse("Hello, world");

        ListenableFuture<Optional<InputStream>> future = async.getOptionalBinary();
        Optional<InputStream> maybeBinary = Futures.getUnchecked(future);

        assertThat(maybeBinary).isPresent();
        assertThat(maybeBinary.get()).hasSameContentAs(asInputStream("Hello, world"));
    }

    @Test
    public void conjure_generated_blocking_interface_with_optional_binary_return_type_and_gzip() {
        setBinaryGzipResponse("Hello, world");

        Optional<InputStream> maybeBinary = blocking.getOptionalBinary();

        assertThat(maybeBinary).isPresent();
        assertThat(maybeBinary.get()).hasSameContentAs(asInputStream("Hello, world"));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true,  false", "true,  true"})
    public void limiting_response_size(boolean belowLimit, boolean gzippedResponse) throws IOException {
        // We surround the content with quotes, so the response size is 2 more than the content
        String content = "a".repeat(belowLimit ? MAX_RESPONSE_SIZE - 2 : MAX_RESPONSE_SIZE - 1);

        String jsonContent = "\"" + content + "\"";

        Response wireResponse = Response.builder()
                .gzipped(gzippedResponse)
                .contents(jsonContent)
                .build();

        if (gzippedResponse) {
            // Even if the gzipped response itself is below the limit, we are measuring the response size after
            // decompression, since the goal is to protect against memory consumption upon deserialization
            assertThat(wireResponse.wireContents().length).isLessThan(MAX_RESPONSE_SIZE);
        }
        undertowHandler = wireResponse.handler();

        if (belowLimit) {
            AliasOfOptional response = blockingMaxResponseSize.getMyAlias();

            assertThat(response.get()).isPresent();
            assertThat(response.get()).hasValue(content);
        } else {
            assertThatException()
                    .isThrownBy(() -> blockingMaxResponseSize.getMyAlias())
                    .isInstanceOf(ResponseSizeTooLargeException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void input_stream_response_sizes_are_not_limited(boolean gzippedResponse) throws IOException {
        String content = "a".repeat(MAX_RESPONSE_SIZE * 2);

        undertowHandler = Response.builder()
                .contentType("application/octet-stream")
                .gzipped(gzippedResponse)
                .contents(content)
                .buildHandler();

        // Even if we are above the limit, input streams can still properly deserialize
        try (InputStream response = blockingMaxResponseSize.getBinary()) {
            assertThat(response).hasContent(content);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void optional_input_stream_response_sizes_are_not_limited(boolean gzippedResponse) throws IOException {
        String content = "a".repeat(MAX_RESPONSE_SIZE * 2);

        undertowHandler = Response.builder()
                .contentType("application/octet-stream")
                .gzipped(gzippedResponse)
                .contents(content)
                .buildHandler();

        // Even if we are above the limit, input streams can still properly deserialize
        try (InputStream response = blockingMaxResponseSize.getOptionalBinary().get()) {
            assertThat(response).hasContent(content);
        }
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true,  false", "true,  true"})
    public void limiting_response_size_for_errors(boolean belowLimit, boolean gzippedResponse) throws IOException {
        String content = "{\"errorCode\":\"errorCode\",\"errorName\":\"" + "test".repeat(belowLimit ? 1 : 128) + "\"}";

        undertowHandler = Response.builder()
                .code(400)
                .gzipped(gzippedResponse)
                .contents(content)
                .buildHandler();

        // No matter where we get the exception from, it should be limited if above the limit,
        // even if a non-error response would not be limited
        Map<String, ThrowingCallable> cases = Map.of(
                "json", () -> blockingMaxResponseSize.getMyAlias(),
                "empty", () -> blockingMaxResponseSize.voidToVoid(),
                "input stream", () -> blockingMaxResponseSize.getBinary(),
                "optional input stream", () -> blockingMaxResponseSize.getOptionalBinary());
        cases.forEach((description, callable) -> {
            if (belowLimit) {
                assertThatException()
                        .describedAs(description)
                        .isThrownBy(callable)
                        .isInstanceOf(RemoteException.class)
                        .extracting(e -> ((RemoteException) e).getError())
                        .isEqualTo(SerializableError.builder()
                                .errorCode("errorCode")
                                .errorName("test")
                                .build());
            } else {
                assertThatException()
                        .describedAs(description)
                        .isThrownBy(callable)
                        .isInstanceOf(ResponseSizeTooLargeException.class);
            }
        });
    }

    @Test
    public void stream_3_gigabytes() throws IOException {
        long oneMegabyte = 1000_000;
        int megabytes = 3000;
        long limit = megabytes * oneMegabyte;
        assertThat(limit).isGreaterThan(Integer.MAX_VALUE);

        byte[] sample = new byte[8192];
        Arrays.fill(sample, (byte) 'A');

        undertowHandler = exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
            try (InputStream bigInputStream = repeat(sample, limit)) {
                bigInputStream.transferTo(exchange.getOutputStream());
            }
        };

        Stopwatch sw = Stopwatch.createStarted();

        SampleServiceBlocking service = blocking;
        InputStream maybeBinary = service.getOptionalBinary().get();
        assertThat(ByteStreams.exhaust(maybeBinary))
                .describedAs("Should receive exactly the number of bytes we sent!")
                .isEqualTo(limit);

        System.out.printf("%d MB took %d millis%n", megabytes, sw.elapsed(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testClosedConnectionIsRetried() {
        AtomicInteger requests = new AtomicInteger();
        undertowHandler = exchange -> {
            if (requests.getAndIncrement() == 0) {
                exchange.getConnection().close();
            } else {
                exchange.setStatusCode(204);
            }
        };
        AliasOfOptional myAlias = blocking.getMyAlias();
        Optional<String> maybeString = myAlias.get();
        assertThat(maybeString).isNotPresent();
        assertThat(requests).hasValue(2);
    }

    @Test
    public void when_thread_is_interrupted_no_requests_are_made() {
        AtomicInteger served = new AtomicInteger();
        undertowHandler = exchange -> {
            served.getAndIncrement();
            exchange.setStatusCode(204);
        };

        Thread.currentThread().interrupt();

        assertThatThrownBy(blocking::getMyAlias)
                .satisfies(throwable ->
                        assertThat(throwable.getClass().getSimpleName()).isEqualTo("DialogueException"))
                .hasCauseInstanceOf(InterruptedException.class);

        ListenableFuture<AliasOfOptional> future = async.getMyAlias();
        assertThat(future).isDone();
        assertThat(future).isNotCancelled();
        assertThatThrownBy(future::get).isInstanceOf(InterruptedException.class);

        assertThat(served).hasValue(0);
    }

    @Test
    public void concurrency_limiters_can_effectively_infer_server_side_ratelimits() throws Exception {
        int permitsPerSecond = 300;
        RateLimiter rateLimiter = RateLimiter.create(permitsPerSecond);

        undertowHandler = exchange -> {
            // These thread.sleep times are taken from a prod instance of internal-ski-product.
            // They're not really crucial to the test, feel free to dial them up or down or omit them entirely.
            if (rateLimiter.tryAcquire()) {
                Thread.sleep(7);
                exchange.setStatusCode(200);
            } else {
                Thread.sleep(22);
                exchange.setStatusCode(429);
            }
        };

        // kick off a big spike of requests (so that we actually max out our concurrency limiter).
        List<ListenableFuture<Void>> futures = IntStream.range(0, permitsPerSecond * 10)
                .mapToObj(_i -> async.voidToVoid())
                .collect(Collectors.toList());

        // Our ConcurrencyLimiters should be able to figure out a sensible bound (based on the RTT) to send requests
        // as close to the server's max rate as possible, without stepping over the line too much and getting 429'd.
        // If we're too aggressive, we run the risk of a single request exhausting all its retries and thereby failing
        // the entire batch.
        ListenableFuture<List<Void>> all = Futures.allAsList(futures);
        all.get();
    }

    private void set204Response() {
        undertowHandler = Response.builder().code(204).contents("").buildHandler();
    }

    private void setBinaryGzipResponse(String stringToCompress) {
        undertowHandler = Response.builder()
                .contentType("application/octet-stream")
                .gzipped(true)
                .contents(stringToCompress)
                .buildHandler();
    }

    @Value.Immutable
    interface Response {
        @Value.Default
        default int code() {
            return 200;
        }

        @Value.Default
        default String contentType() {
            return "application/json";
        }

        @Value.Default
        default boolean gzipped() {
            return false;
        }

        String contents();

        @Value.Derived
        default byte[] wireContents() {
            try {
                return gzipped() ? gzipCompress(contents()) : contents().getBytes(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Value.Derived
        default HttpHandler handler() {
            return exchange -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType());
                exchange.setStatusCode(code());

                if (gzipped()) {
                    Preconditions.checkArgument(exchange.getRequestHeaders().contains(Headers.ACCEPT_ENCODING));
                    Preconditions.checkArgument(exchange.getRequestHeaders()
                            .getFirst(Headers.ACCEPT_ENCODING)
                            .contains("gzip"));
                    exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, "gzip");
                }
                exchange.getOutputStream().write(wireContents());
            };
        }

        static Builder builder() {
            return new Builder();
        }

        final class Builder extends ImmutableResponse.Builder {
            public HttpHandler buildHandler() {
                return build().handler();
            }
        }
    }

    @AfterEach
    public void after() {
        undertow.stop();
    }

    private static ByteArrayInputStream asInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] gzipCompress(String stringToCompress) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gzipOutput = new GZIPOutputStream(baos)) {
            gzipOutput.write(stringToCompress.getBytes(StandardCharsets.UTF_8));
            gzipOutput.finish();
            return baos.toByteArray();
        }
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format("%s:/%s", listenerInfo.getProtcol(), listenerInfo.getAddress());
    }

    /** Produces a big inputStream by repeating a smaller bytearray sample until limit is reached. */
    private static InputStream repeat(byte[] sample, long limit) {
        return new InputStream() {
            private long position = 0;

            @Override
            public int read() {
                if (position < limit) {
                    return sample[(int) (position++ % sample.length)];
                } else {
                    return -1;
                }
            }

            // this optimized version isn't really necessary, I just wanted to see how fast we could make
            // the test go
            @Override
            public int read(byte[] outputArray, int off, int len) {
                long remainingInStream = limit - position;
                if (remainingInStream <= 0) {
                    return -1;
                }

                int numBytesToWrite = (int) Math.min(len, remainingInStream);
                int bytesWritten = 0;
                while (bytesWritten < numBytesToWrite) {
                    int sampleIndex = (int) position % sample.length;
                    int outputIndex = off + bytesWritten;
                    int chunkSize = Math.min(sample.length - sampleIndex, numBytesToWrite - bytesWritten);

                    System.arraycopy(sample, sampleIndex, outputArray, outputIndex, chunkSize);
                    position += chunkSize;
                    bytesWritten += chunkSize;
                }

                return bytesWritten;
            }

            @Override
            public int available() {
                return Ints.saturatedCast(limit - position);
            }
        };
    }
}
