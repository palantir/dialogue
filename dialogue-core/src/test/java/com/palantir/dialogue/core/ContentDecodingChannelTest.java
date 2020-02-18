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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.guava.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.DialogueImmutablesStyle;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.assertj.core.data.MapEntry;
import org.immutables.value.Value;
import org.junit.Test;

public final class ContentDecodingChannelTest {

    @Test
    public void testDecoding() throws Exception {
        byte[] expected = new byte[] {1, 2, 3, 4};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream compressor = new GZIPOutputStream(out)) {
            compressor.write(expected);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Response response = new ContentDecodingChannel(
                        (endpoint, request) -> Futures.immediateFuture(StubResponse.builder()
                                .body(new ByteArrayInputStream(out.toByteArray()))
                                .putHeaders("content-encoding", ImmutableList.of("gzip"))
                                .putHeaders("content-length", ImmutableList.of(Integer.toString(out.size())))
                                .build()))
                .execute(TestEndpoint.INSTANCE, Request.builder().build())
                .get();
        assertThat(response.headers()).doesNotContainKey("content-encoding");
        assertThat(ByteStreams.toByteArray(response.body())).containsExactly(expected);
    }

    @Test
    public void testDecoding_delayedFailure() throws Exception {
        Response response = new ContentDecodingChannel(
                        (endpoint, request) -> Futures.immediateFuture(StubResponse.builder()
                                // Will fail because it's not valid gzip content
                                .body(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}))
                                .putHeaders("content-encoding", ImmutableList.of("gzip"))
                                .build()))
                .execute(TestEndpoint.INSTANCE, Request.builder().build())
                .get();
        assertThat(response.headers()).doesNotContainKey("content-encoding");
        assertThatThrownBy(response.body()::read).isInstanceOf(IOException.class);
    }

    @Test
    public void testOnlyDecodesGzip() throws Exception {
        byte[] content = new byte[] {1, 2, 3, 4};
        Response response = new ContentDecodingChannel(
                        (endpoint, request) -> Futures.immediateFuture(StubResponse.builder()
                                .body(new ByteArrayInputStream(content))
                                .putHeaders("content-encoding", ImmutableList.of("unknown"))
                                .build()))
                .execute(TestEndpoint.INSTANCE, Request.builder().build())
                .get();
        assertThat(response.headers()).containsEntry("content-encoding", ImmutableList.of("unknown"));
        assertThat(ByteStreams.toByteArray(response.body())).isEqualTo(content);
    }

    @Test
    public void testRequestHeader() throws Exception {
        new ContentDecodingChannel((endpoint, request) -> {
                    assertThat(request.headerParams()).contains(MapEntry.entry("accept-encoding", "gzip"));
                    return Futures.immediateFuture(StubResponse.builder().build());
                })
                .execute(TestEndpoint.INSTANCE, Request.builder().build())
                .get();
    }

    @Test
    public void testRequestHeader_existingIsNotReplaced() throws Exception {
        new ContentDecodingChannel((endpoint, request) -> {
                    assertThat(request.headerParams())
                            .as("The requested 'identity' encoding should not be replaced")
                            .contains(MapEntry.entry("accept-encoding", "identity"));
                    return Futures.immediateFuture(StubResponse.builder().build());
                })
                .execute(
                        TestEndpoint.INSTANCE,
                        Request.builder()
                                .putHeaderParams("accept-encoding", "identity")
                                .build())
                .get();
    }

    @Test
    public void testResponseHeaders() throws Exception {
        new ContentDecodingChannel((endpoint, request) -> {
                    assertThat(request.headerParams())
                            .as("The requested 'identity' encoding should not be replaced")
                            .contains(MapEntry.entry("accept-encoding", "identity"));
                    return Futures.immediateFuture(StubResponse.builder().build());
                })
                .execute(
                        TestEndpoint.INSTANCE,
                        Request.builder()
                                .putHeaderParams("accept-encoding", "identity")
                                .build())
                .get();
    }

    private enum TestEndpoint implements Endpoint {
        INSTANCE;

        @Override
        public void renderPath(Map<String, String> _params, UrlBuilder _url) {}

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
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
            return "1.0.0";
        }
    }

    @DialogueImmutablesStyle
    @Value.Immutable
    interface StubResponse extends Response {

        @Override
        @Value.Default
        default InputStream body() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        @Value.Default
        default int code() {
            return 200;
        }

        @Override
        @Value.Default
        default Map<String, List<String>> headers() {
            return ImmutableMap.of();
        }

        @Override
        default void close() {}

        class Builder extends ImmutableStubResponse.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }
}
