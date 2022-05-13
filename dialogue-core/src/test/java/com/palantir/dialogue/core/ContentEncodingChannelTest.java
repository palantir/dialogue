/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.TestEndpoint;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class ContentEncodingChannelTest {

    @Test
    void testCompression() {
        byte[] expected = "Hello".getBytes(StandardCharsets.UTF_8);
        Request request = Request.builder()
                .body(new RequestBody() {
                    @Override
                    public void writeTo(OutputStream output) throws IOException {
                        output.write(expected);
                    }

                    @Override
                    public String contentType() {
                        return "text/plain";
                    }

                    @Override
                    public boolean repeatable() {
                        return true;
                    }

                    @Override
                    public OptionalLong contentLength() {
                        return OptionalLong.of(expected.length);
                    }

                    @Override
                    public void close() {}
                })
                .build();
        Request wrapped = ContentEncodingChannel.wrap(request);
        assertThat(wrapped.body()).hasValueSatisfying(body -> {
            assertThat(body.contentLength()).isEmpty();
            assertThat(inflate(content(body))).isEqualTo(expected);
        });
    }

    @Test
    void testNoBody() {
        Request request = Request.builder().build();
        Request wrapped = ContentEncodingChannel.wrap(request);
        assertThat(wrapped).isSameAs(request);
    }

    @Test
    void testContentEncodingExists() {
        Request request = Request.builder()
                .putHeaderParams("Content-Encoding", "identity")
                .body(StubBody.INSTANCE)
                .build();
        Request wrapped = ContentEncodingChannel.wrap(request);
        assertThat(wrapped).isSameAs(request);
    }

    @Test
    void testContentLengthExistsOutsideBody() {
        // This case shouldn't be supported in any way, however
        // it's best to handle unexpected cases gracefully.
        Request request = Request.builder()
                .putHeaderParams("Content-Length", "123")
                .body(StubBody.INSTANCE)
                .build();
        Request wrapped = ContentEncodingChannel.wrap(request);
        assertThat(wrapped).isSameAs(request);
    }

    @Test
    void testChannelCreationWithTag() {
        EndpointChannel delegate = _req -> Futures.immediateCancelledFuture();
        EndpointChannel result = ContentEncodingChannel.of(delegate, new Endpoint() {

            @Override
            public HttpMethod httpMethod() {
                return HttpMethod.POST;
            }

            @Override
            public String serviceName() {
                return "service";
            }

            @Override
            public String endpointName() {
                return "endpoint";
            }

            @Override
            public String version() {
                return "1.2.3";
            }

            public Set<String> tags() {
                return ImmutableSet.of("compress-request");
            }
        });
        assertThat(result).isNotSameAs(delegate);
    }

    @Test
    void testChannelCreationWithoutTag() {
        EndpointChannel delegate = _req -> Futures.immediateCancelledFuture();
        EndpointChannel result = ContentEncodingChannel.of(delegate, TestEndpoint.POST);
        assertThat(result).isSameAs(delegate);
    }

    private static byte[] content(RequestBody body) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            body.writeTo(baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    private static byte[] inflate(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(bais)) {
            return ByteStreams.toByteArray(gzipInputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private enum StubBody implements RequestBody {
        INSTANCE;

        @Override
        public void writeTo(OutputStream output) throws IOException {
            output.write("test".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String contentType() {
            return "text/plain";
        }

        @Override
        public boolean repeatable() {
            return false;
        }

        @Override
        public void close() {}
    }
}
