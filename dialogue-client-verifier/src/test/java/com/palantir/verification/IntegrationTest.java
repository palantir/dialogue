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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.example.AliasOfAliasOfOptional;
import com.palantir.dialogue.example.AliasOfOptional;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import com.palantir.logsafe.Preconditions;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class IntegrationTest {
    private static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("BinaryReturnTypeTest", "0.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("../dialogue-core/src/test/resources/trustStore.jks"),
            Paths.get("../dialogue-core/src/test/resources/keyStore.jks"),
            "keystore");

    private Undertow undertow;
    private HttpHandler undertowHandler;

    @Before
    public void before() {
        undertow = Undertow.builder()
                .addHttpListener(
                        0, "localhost", new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();
    }

    @Test
    public void alias_of_optional() {
        set204Response();
        AliasOfOptional myAlias = sampleServiceBlocking().getMyAlias();
        Optional<String> maybeString = myAlias.get();
        assertThat(maybeString).isNotPresent();
    }

    @Test
    public void alias_of_alias_of_optional() {
        set204Response();
        AliasOfAliasOfOptional myAlias = sampleServiceBlocking().getMyAlias2();
        Optional<String> maybeString = myAlias.get().get();
        assertThat(maybeString).isNotPresent();
    }

    @Test
    public void conjure_generated_async_interface_with_optional_binary_return_type_and_gzip() {
        setBinaryGzipResponse("Hello, world");
        SampleServiceAsync client = sampleServiceAsync();

        ListenableFuture<Optional<InputStream>> future = client.getOptionalBinary();
        Optional<InputStream> maybeBinary = Futures.getUnchecked(future);

        assertThat(maybeBinary).isPresent();
        assertThat(maybeBinary.get()).hasSameContentAs(asInputStream("Hello, world"));
    }

    @Test
    public void conjure_generated_blocking_interface_with_optional_binary_return_type_and_gzip() {
        setBinaryGzipResponse("Hello, world");

        Optional<InputStream> maybeBinary = sampleServiceBlocking().getOptionalBinary();

        assertThat(maybeBinary).isPresent();
        assertThat(maybeBinary.get()).hasSameContentAs(asInputStream("Hello, world"));
    }

    @Test
    public void deserializes_a_conjure_error_after_exhausting_retries() {
        AtomicInteger calls = new AtomicInteger(0);
        undertowHandler = exchange -> {
            exchange.setStatusCode(429);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender()
                    .send("{"
                            + "\"errorCode\":\"FAILED_PRECONDITION\","
                            + "\"errorName\":\"Default:FailedPrecondition\","
                            + "\"errorInstanceId\":\"43580df1-e019-473b-bb3d-be6d489f36e5\","
                            + "\"parameters\":{\"numCalls\":\"" + calls.getAndIncrement() + "\"}"
                            + "}\n");
        };

        assertThatThrownBy(sampleServiceBlocking()::voidToVoid)
                .isInstanceOf(RemoteException.class)
                .satisfies(throwable -> {
                    assertThat(((RemoteException) throwable).getError().parameters())
                            .containsEntry("numCalls", "4");
                });

        assertThat(calls).describedAs("one initial call + 4 retries").hasValue(5);
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
            InputStream bigInputStream = repeat(sample, limit);
            ByteStreams.copy(bigInputStream, exchange.getOutputStream());
        };

        Stopwatch sw = Stopwatch.createStarted();

        InputStream maybeBinary = sampleServiceBlocking().getOptionalBinary().get();
        assertThat(ByteStreams.exhaust(maybeBinary))
                .describedAs("Should receive exactly the number of bytes we sent!")
                .isEqualTo(limit);

        System.out.printf("%d MB took %d millis%n", megabytes, sw.elapsed(TimeUnit.MILLISECONDS));
    }

    private void set204Response() {
        undertowHandler = exchange -> {
            exchange.setStatusCode(204);
        };
    }

    private void setBinaryGzipResponse(String stringToCompress) {
        undertowHandler = exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
            Preconditions.checkArgument(exchange.getRequestHeaders().contains(Headers.ACCEPT_ENCODING));
            Preconditions.checkArgument(exchange.getRequestHeaders()
                    .getFirst(Headers.ACCEPT_ENCODING)
                    .contains("gzip"));
            exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, "gzip");
            exchange.getOutputStream().write(gzipCompress(stringToCompress));
        };
    }

    private SampleServiceBlocking sampleServiceBlocking() {
        return SampleServiceBlocking.of(
                ApacheHttpClientChannels.create(clientConf(getUri(undertow))),
                DefaultConjureRuntime.builder().build());
    }

    private SampleServiceAsync sampleServiceAsync() {
        return SampleServiceAsync.of(
                ApacheHttpClientChannels.create(clientConf(getUri(undertow))),
                DefaultConjureRuntime.builder().build());
    }

    @After
    public void after() {
        undertow.stop();
    }

    private static ByteArrayInputStream asInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    private static ClientConfiguration clientConf(String uri) {
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris(uri)
                        .security(SSL_CONFIG)
                        .readTimeout(Duration.ofSeconds(1))
                        .writeTimeout(Duration.ofSeconds(1))
                        .connectTimeout(Duration.ofSeconds(1))
                        .build()))
                .userAgent(USER_AGENT)
                .build();
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
