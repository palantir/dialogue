/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Snapshot;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RequestSizeMetricsChannelTest {
    @Mock
    DialogueChannelFactory factory;

    @Test
    public void records_request_size_metrics() throws ExecutionException, InterruptedException {
        TaggedMetricRegistry registry = DefaultTaggedMetricRegistry.getDefault();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] expected = "test request body".getBytes(StandardCharsets.UTF_8);
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

        EndpointChannel channel = RequestSizeMetricsChannel.create(
                config(ClientConfiguration.builder()
                        .from(TestConfigurations.create("https://foo:10001"))
                        .taggedMetricRegistry(registry)
                        .build()),
                r -> {
                    try {
                        RequestBody body = r.body().get();
                        body.writeTo(baos);
                        body.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return Futures.immediateFuture(new TestResponse().code(200));
                },
                TestEndpoint.GET);
        ListenableFuture<Response> response = channel.execute(request);

        assertThat(response.get().code()).isEqualTo(200);
        Snapshot snapshot = DialogueClientMetrics.of(registry)
                .requestsSize()
                .serviceName("service")
                .endpoint("endpoint")
                .build()
                .getSnapshot();
        assertThat(snapshot.size()).isEqualTo(1);
        assertThat(snapshot.get99thPercentile()).isEqualTo(expected.length);
    }

    private ImmutableConfig config(ClientConfiguration rawConfig) {
        return ImmutableConfig.builder()
                .channelName("channelName")
                .channelFactory(factory)
                .rawConfig(rawConfig)
                .build();
    }
}
