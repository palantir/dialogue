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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;

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
                        (endpoint, request) -> Futures.immediateFuture(new TestResponse(out.toByteArray())
                                .withHeader("content-encoding", "gzip")
                                .withHeader("content-length", Integer.toString(out.size()))))
                .execute(TestEndpoint.INSTANCE, Request.builder().build())
                .get();
        assertThat(response.headers().get("content-encoding")).isEmpty();
        assertThat(ByteStreams.toByteArray(response.body())).containsExactly(expected);
    }

    @Test
    public void testDecoding_delayedFailure() throws Exception {
        Response response = new ContentDecodingChannel((endpoint, request) -> Futures.immediateFuture(
                        // Will fail because it's not valid gzip content
                        new TestResponse(new byte[] {1, 2, 3, 4}).withHeader("content-encoding", "gzip")))
                .execute(TestEndpoint.INSTANCE, Request.builder().build())
                .get();
        assertThat(response.headers().get("content-encoding")).isEmpty();
        assertThatThrownBy(response.body()::read).isInstanceOf(IOException.class);
    }

    @Test
    public void testOnlyDecodesGzip() throws Exception {
        byte[] content = new byte[] {1, 2, 3, 4};
        Response response = new ContentDecodingChannel((endpoint, request) ->
                        Futures.immediateFuture(new TestResponse(content).withHeader("content-encoding", "unknown")))
                .execute(TestEndpoint.INSTANCE, Request.builder().build())
                .get();
        assertThat(response.headers()).containsAllEntriesOf(ImmutableListMultimap.of("content-encoding", "unknown"));
        assertThat(ByteStreams.toByteArray(response.body())).isEqualTo(content);
    }

    @Test
    public void testRequestHeader() throws Exception {
        new ContentDecodingChannel((endpoint, request) -> {
                    assertThat(request.headerParams()).contains(MapEntry.entry("accept-encoding", "gzip"));
                    return Futures.immediateFuture(new TestResponse());
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
                    return Futures.immediateFuture(new TestResponse());
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
                    return Futures.immediateFuture(new TestResponse());
                })
                .execute(
                        TestEndpoint.INSTANCE,
                        Request.builder()
                                .putHeaderParams("accept-encoding", "identity")
                                .build())
                .get();
    }
}
