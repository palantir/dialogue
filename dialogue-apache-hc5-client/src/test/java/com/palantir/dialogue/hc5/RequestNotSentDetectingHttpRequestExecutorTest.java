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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityTemplate;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.junit.jupiter.api.Test;

final class RequestNotSentDetectingHttpRequestExecutorTest {

    @Test
    void throwsRequestNotSentWhenFirstWriteFails() throws Exception {
        TimingOutSocket socket = new TimingOutSocket();
        ManagedHttpClientConnection connection = connection(socket);
        try (ClassicHttpResponse response = RequestNotSentDetectingHttpRequestExecutor.INSTANCE.execute(
                post(body(1024)), connection, HttpCoreContext.create())) {
            assertThat(response.getCode()).isEqualTo(200);
        }

        socket.timeOutAfterWrites(0);

        assertThatThrownBy(() -> RequestNotSentDetectingHttpRequestExecutor.INSTANCE.execute(
                        post(body(1024)), connection, HttpCoreContext.create()))
                .isExactlyInstanceOf(SafeRequestNotSentException.class)
                .hasCauseExactlyInstanceOf(SocketException.class)
                .hasRootCauseMessage("Connection timed out");
    }

    @Test
    void rethrowsWhenPartOfRequestWasSent() throws Exception {
        TimingOutSocket socket = new TimingOutSocket();
        socket.timeOutAfterWrites(1);

        // Larger than Apache's 32 KiB output buffer, so the headers and the body are separate socket writes.
        assertThatThrownBy(() -> RequestNotSentDetectingHttpRequestExecutor.INSTANCE.execute(
                        post(body(64 * 1024)), connection(socket), HttpCoreContext.create()))
                .isExactlyInstanceOf(SocketException.class)
                .hasMessage("Connection timed out");
    }

    @Test
    void rethrowsWhenRequestBodyFailsOnLiveConnection() throws Exception {
        assertThatThrownBy(() -> RequestNotSentDetectingHttpRequestExecutor.INSTANCE.execute(
                        post(failingBody()), connection(new TimingOutSocket()), HttpCoreContext.create()))
                .isExactlyInstanceOf(SocketTimeoutException.class)
                .hasMessage("Read timed out");
    }

    private static ManagedHttpClientConnection connection(Socket socket) throws IOException {
        return ManagedHttpClientConnectionFactory.INSTANCE.createConnection(socket);
    }

    private static ClassicHttpRequest post(HttpEntity entity) throws HttpException, IOException {
        ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.setEntity(entity);
        // Executing directly skips Apache's interceptors, so apply the one that frames the body.
        RequestContent.INSTANCE.process(request, entity, HttpCoreContext.create());
        return request;
    }

    private static HttpEntity body(int size) {
        return new ByteArrayEntity(new byte[size], ContentType.APPLICATION_OCTET_STREAM);
    }

    private static HttpEntity failingBody() {
        return new EntityTemplate(ContentType.APPLICATION_OCTET_STREAM, _output -> {
            throw new SocketTimeoutException("Read timed out");
        });
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
