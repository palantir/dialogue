/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityTemplate;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class InstrumentedManagedHttpClientConnectionTest {

    @ParameterizedTest
    @MethodSource
    public void reportsRequestNotSentWhenReusedConnectionTimedOut(ClassicHttpRequest request) throws Exception {
        TimingOutSocket socket = new TimingOutSocket();
        ManagedHttpClientConnection connection = connect(socket);
        try (ClassicHttpResponse response =
                new HttpRequestExecutor().execute(chunkedPost(1024), connection, HttpCoreContext.create())) {
            assertThat(response.getCode()).isEqualTo(200);
        }

        socket.timeOutAfterWrites(0);

        assertThatThrownBy(() -> new HttpRequestExecutor().execute(request, connection, HttpCoreContext.create()))
                .isExactlyInstanceOf(SafeRequestNotSentException.class)
                .hasCauseExactlyInstanceOf(SocketException.class)
                .hasRootCauseMessage("Connection timed out");
    }

    static List<Arguments> reportsRequestNotSentWhenReusedConnectionTimedOut() {
        ClassicHttpRequest largeHeaders = new BasicClassicHttpRequest(Method.GET, "/");
        largeHeaders.setHeader("Large", "a".repeat(64 * 1024));
        return List.of(
                argumentSet("withBody", chunkedPost(1024)),
                argumentSet("withoutBody", new BasicClassicHttpRequest(Method.GET, "/")),
                argumentSet("withLargeHeaders", largeHeaders));
    }

    @Test
    public void rethrowsFailureAfterPartOfRequestWasSent() throws Exception {
        TimingOutSocket socket = new TimingOutSocket();
        socket.timeOutAfterWrites(1);

        assertThatThrownBy(() -> new HttpRequestExecutor()
                        .execute(chunkedPost(64 * 1024), connect(socket), HttpCoreContext.create()))
                .isExactlyInstanceOf(SocketException.class)
                .hasMessage("Connection timed out");
    }

    @Test
    public void rethrowsRequestBodyFailure() throws Exception {
        ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING);
        request.setEntity(new EntityTemplate(ContentType.APPLICATION_OCTET_STREAM, _output -> {
            throw new SocketTimeoutException("Read timed out");
        }));

        assertThatThrownBy(() -> new HttpRequestExecutor()
                        .execute(request, connect(new TimingOutSocket()), HttpCoreContext.create()))
                .isExactlyInstanceOf(SocketTimeoutException.class)
                .hasMessage("Read timed out");
    }

    private static ManagedHttpClientConnection connect(Socket socket) throws IOException {
        return new InstrumentedManagedHttpConnectionFactory(
                        ManagedHttpClientConnectionFactory.INSTANCE, new DefaultTaggedMetricRegistry(), "test")
                .createConnection(socket);
    }

    private static ClassicHttpRequest chunkedPost(int bodySize) {
        ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING);
        request.setEntity(new ByteArrayEntity(new byte[bodySize], ContentType.APPLICATION_OCTET_STREAM));
        return request;
    }

    // Throws the error the JDK reports for ETIMEDOUT on Linux, which RetryingChannel does not retry by default.
    private static final class TimingOutSocket extends Socket {
        private final InputStream input = new ByteArrayInputStream(
                "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        private int remainingWrites = Integer.MAX_VALUE;

        void timeOutAfterWrites(int writes) {
            remainingWrites = writes;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    write(new byte[] {(byte) value}, 0, 1);
                }

                @Override
                public void write(byte[] _bytes, int _offset, int _length) throws IOException {
                    if (remainingWrites == 0) {
                        throw new SocketException("Connection timed out");
                    }
                    remainingWrites--;
                }
            };
        }
    }
}
