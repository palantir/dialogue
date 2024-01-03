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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public final class ContentDecodingChannelTest {

    private Config standard;
    private Config mesh;

    @BeforeEach
    void before() throws Exception {
        X509TrustManager tm = Mockito.mock(X509TrustManager.class);
        ClientConfiguration clientConfig = ClientConfigurations.of(
                ImmutableList.of("https://localhost:8123"),
                SSLContext.getDefault().getSocketFactory(),
                tm);
        standard = Mockito.mock(Config.class);
        Mockito.when(standard.mesh()).thenReturn(MeshMode.DEFAULT_NO_MESH);
        Mockito.when(standard.clientConf()).thenReturn(clientConfig);
        mesh = Mockito.mock(Config.class);
        Mockito.when(mesh.mesh()).thenReturn(MeshMode.USE_EXTERNAL_MESH);
        Mockito.when(mesh.clientConf()).thenReturn(clientConfig);
    }

    @Test
    public void testDecoding() throws Exception {
        byte[] expected = new byte[] {1, 2, 3, 4};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream compressor = new GZIPOutputStream(out)) {
            compressor.write(expected);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Response response = ContentDecodingChannel.create(
                        standard,
                        _request -> Futures.immediateFuture(new TestResponse(out.toByteArray())
                                .withHeader("content-encoding", "gzip")
                                .withHeader("content-length", Integer.toString(out.size()))),
                        TestEndpoint.GET)
                .execute(Request.builder().build())
                .get();
        assertThat(response.headers().get("content-encoding")).isEmpty();
        assertThat(ByteStreams.toByteArray(response.body())).containsExactly(expected);
    }

    // In mesh mode, decoding should continue to work, but the accept-encoding header will not be sent
    // in order to hint that we don't want the server to encode.
    @Test
    public void testDecoding_mesh() throws Exception {
        byte[] expected = new byte[] {1, 2, 3, 4};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream compressor = new GZIPOutputStream(out)) {
            compressor.write(expected);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Response response = ContentDecodingChannel.create(
                        mesh,
                        _request -> Futures.immediateFuture(new TestResponse(out.toByteArray())
                                .withHeader("content-encoding", "gzip")
                                .withHeader("content-length", Integer.toString(out.size()))),
                        TestEndpoint.GET)
                .execute(Request.builder().build())
                .get();
        assertThat(response.headers().get("content-encoding")).isEmpty();
        assertThat(ByteStreams.toByteArray(response.body())).containsExactly(expected);
    }

    @Test
    public void testDecoding_delayedFailure() throws Exception {
        // Will fail because it's not valid gzip content
        Response response = ContentDecodingChannel.create(
                        standard,
                        _request -> Futures.immediateFuture(
                                // Will fail because it's not valid gzip content
                                new TestResponse(new byte[] {1, 2, 3, 4}).withHeader("content-encoding", "gzip")),
                        TestEndpoint.GET)
                .execute(Request.builder().build())
                .get();
        assertThat(response.headers().get("content-encoding")).isEmpty();
        try (InputStream body = response.body()) {
            assertThatThrownBy(body::read).isInstanceOf(IOException.class);
        }
    }

    @Test
    public void testOnlyDecodesGzip() throws Exception {
        byte[] content = new byte[] {1, 2, 3, 4};
        Response response = ContentDecodingChannel.create(
                        standard,
                        _request -> Futures.immediateFuture(
                                new TestResponse(content).withHeader("content-encoding", "unknown")),
                        TestEndpoint.GET)
                .execute(Request.builder().build())
                .get();
        assertThat(response.headers()).containsAllEntriesOf(ImmutableListMultimap.of("content-encoding", "unknown"));
        assertThat(ByteStreams.toByteArray(response.body())).isEqualTo(content);
    }

    @Test
    public void testRequestHeader() throws Exception {
        ContentDecodingChannel.create(
                        standard,
                        request -> {
                            assertThat(request.headerParams()).contains(MapEntry.entry("accept-encoding", "gzip"));
                            return Futures.immediateFuture(new TestResponse());
                        },
                        TestEndpoint.GET)
                .execute(Request.builder().build())
                .get();
    }

    @Test
    public void testRequestHeader_mesh() throws Exception {
        ContentDecodingChannel.create(
                        mesh,
                        request -> {
                            assertThat(request.headerParams().keySet()).doesNotContain("accept-encoding");
                            return Futures.immediateFuture(new TestResponse());
                        },
                        TestEndpoint.GET)
                .execute(Request.builder().build())
                .get();
    }

    @Test
    public void testRequestHeader_meshWithOverride() throws Exception {
        ContentDecodingChannel.create(
                        mesh,
                        request -> {
                            assertThat(request.headerParams()).contains(MapEntry.entry("accept-encoding", "gzip"));
                            return Futures.immediateFuture(new TestResponse());
                        },
                        PreferCompressedResponseEndpoint.INSTANCE)
                .execute(Request.builder().build())
                .get();
    }

    @Test
    public void testRequestHeader_existingIsNotReplaced() throws Exception {
        ContentDecodingChannel.create(
                        standard,
                        request -> {
                            assertThat(request.headerParams())
                                    .as("The requested 'identity' encoding should not be replaced")
                                    .contains(MapEntry.entry("accept-encoding", "identity"));
                            return Futures.immediateFuture(new TestResponse());
                        },
                        TestEndpoint.GET)
                .execute(Request.builder()
                        .putHeaderParams("accept-encoding", "identity")
                        .build())
                .get();
    }

    @Test
    public void testResponseHeaders() throws Exception {
        ContentDecodingChannel.create(
                        standard,
                        request -> {
                            assertThat(request.headerParams())
                                    .as("The requested 'identity' encoding should not be replaced")
                                    .contains(MapEntry.entry("accept-encoding", "identity"));
                            return Futures.immediateFuture(new TestResponse());
                        },
                        TestEndpoint.GET)
                .execute(Request.builder()
                        .putHeaderParams("accept-encoding", "identity")
                        .build())
                .get();
    }

    private enum PreferCompressedResponseEndpoint implements Endpoint {
        INSTANCE;

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String serviceName() {
            return "Test";
        }

        @Override
        public String endpointName() {
            return "test";
        }

        @Override
        public String version() {
            return "0.0.0";
        }

        @Override
        public Set<String> tags() {
            return ImmutableSet.of("prefer-compressed-response");
        }
    }
}
