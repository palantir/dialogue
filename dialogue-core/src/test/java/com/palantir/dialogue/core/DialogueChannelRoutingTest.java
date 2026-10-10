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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Futures;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingKey;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.refreshable.Refreshable;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class DialogueChannelRoutingTest {
    private static final List<TargetUri> TARGETS =
            List.of(TargetUri.of("http://a"), TargetUri.of("http://b"), TargetUri.of("http://c"));
    private static final ClientConfiguration CONFIG = ClientConfiguration.builder()
            .from(ClientConfigurations.of(ServiceConfiguration.builder()
                    .addUris("http://a", "http://b", "http://c")
                    .security(SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks")))
                    .build()))
            .userAgent(com.palantir.conjure.java.api.config.service.UserAgent.of(
                    com.palantir.conjure.java.api.config.service.UserAgent.Agent.of("routing-test", "1.0.0")))
            .maxNumRetries(2)
            .backoffSlotSize(Duration.ZERO)
            .build();

    @Test
    void fullPipelineRetriesAnotherNodeAndResetsForTheNextCall() throws Exception {
        List<String> attempts = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean(true);
        DialogueChannel channel = DialogueChannel.builder()
                .channelName("affinity")
                .clientConfiguration(CONFIG)
                .uris(Refreshable.only(TARGETS))
                .factory(args -> (_endpoint, request) -> {
                    attempts.add(args.uri());
                    assertThat(request.headerParams().keySet())
                            .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT)
                                            .contains("routing")
                                    || name.toLowerCase(java.util.Locale.ROOT).contains("hash"));
                    if (fail.getAndSet(false)) {
                        return Futures.immediateFailedFuture(new IOException("transport failure"));
                    }
                    return Futures.immediateFuture(new TestResponse().code(204));
                })
                .build();
        Request request = request();
        try (Response response = channel.execute(TestEndpoint.GET, request).get()) {
            assertThat(response.code()).isEqualTo(204);
        }
        assertThat(attempts).hasSize(2).doesNotHaveDuplicates();
        String preferred = attempts.get(0);
        try (Response response = channel.execute(TestEndpoint.GET, request).get()) {
            assertThat(response.code()).isEqualTo(204);
        }
        assertThat(attempts.get(2)).isEqualTo(preferred);
    }

    @Test
    void retryableUnavailableMovesButRetryBudgetRemainsAuthoritative() throws Exception {
        for (int retries : List.of(0, 2)) {
            List<String> attempts = new ArrayList<>();
            List<TestResponse> responses = new ArrayList<>();
            DialogueChannel channel = DialogueChannel.builder()
                    .channelName("affinity-qos")
                    .clientConfiguration(ClientConfiguration.builder()
                            .from(CONFIG)
                            .maxNumRetries(retries)
                            .build())
                    .uris(Refreshable.only(TARGETS))
                    .factory(args -> (_endpoint, _request) -> {
                        attempts.add(args.uri());
                        TestResponse response = new TestResponse().code(attempts.size() == 1 ? 503 : 204);
                        responses.add(response);
                        return Futures.immediateFuture(response);
                    })
                    .build();
            try (Response response =
                    channel.execute(TestEndpoint.GET, request()).get()) {
                assertThat(response.code()).isEqualTo(retries == 0 ? 503 : 204);
            }
            assertThat(attempts).hasSize(retries == 0 ? 1 : 2).doesNotHaveDuplicates();
            assertThat(responses).allMatch(TestResponse::isClosed);
        }
    }

    @Test
    void liveTargetsRetainChoiceAcrossReorderingAndSelectAnotherOnRemoval() throws Exception {
        com.palantir.refreshable.SettableRefreshable<List<TargetUri>> targets = Refreshable.create(TARGETS);
        AtomicReference<String> selected = new AtomicReference<>();
        DialogueChannel channel = DialogueChannel.builder()
                .channelName("affinity-reload")
                .clientConfiguration(CONFIG)
                .uris(targets)
                .factory(args -> (_endpoint, _request) -> {
                    selected.set(args.uri());
                    return Futures.immediateFuture(new TestResponse().code(204));
                })
                .build();
        channel.execute(TestEndpoint.GET, request()).get().close();
        String preferred = selected.get();
        targets.update(List.of(TARGETS.get(2), TARGETS.get(0), TARGETS.get(1)));
        channel.execute(TestEndpoint.GET, request()).get().close();
        assertThat(selected.get()).isEqualTo(preferred);
        targets.update(TARGETS.stream()
                .filter(target -> !target.uri().equals(preferred))
                .toList());
        channel.execute(TestEndpoint.GET, request()).get().close();
        assertThat(selected.get()).isNotEqualTo(preferred);
    }

    @Test
    void doNotRetryAndNonRepeatableBodiesKeepExistingSemantics() throws Exception {
        AtomicBoolean consume = new AtomicBoolean(false);
        List<String> attempts = new ArrayList<>();
        DialogueChannel channel = DialogueChannel.builder()
                .channelName("affinity-no-retry")
                .clientConfiguration(CONFIG)
                .uris(Refreshable.only(TARGETS))
                .factory(args -> (_endpoint, request) -> {
                    attempts.add(args.uri());
                    if (consume.get()) {
                        try {
                            request.body().orElseThrow().writeTo(java.io.OutputStream.nullOutputStream());
                        } catch (IOException e) {
                            return Futures.immediateFailedFuture(e);
                        }
                        return Futures.immediateFailedFuture(new IOException("after sending body"));
                    }
                    TestResponse response = new TestResponse().code(503);
                    com.palantir.conjure.java.api.errors.QosReasons.encodeToResponse(
                            com.palantir.conjure.java.api.errors.QosReason.builder()
                                    .reason("test")
                                    .retryHint(com.palantir.conjure.java.api.errors.QosReason.RetryHint.DO_NOT_RETRY)
                                    .build(),
                            response,
                            com.palantir.dialogue.TestResponseQosEncoder.INSTANCE);
                    return Futures.immediateFuture(response);
                })
                .build();
        try (Response response = channel.execute(TestEndpoint.GET, request()).get()) {
            assertThat(response.code()).isEqualTo(503);
        }
        assertThat(attempts).hasSize(1);
        consume.set(true);
        Request body = Request.builder()
                .from(request())
                .body(new com.palantir.dialogue.RequestBody() {
                    @Override
                    public void writeTo(java.io.OutputStream _output) {}

                    @Override
                    public String contentType() {
                        return "application/octet-stream";
                    }

                    @Override
                    public boolean repeatable() {
                        return false;
                    }

                    @Override
                    public void close() {}
                })
                .build();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> channel.execute(TestEndpoint.POST, body).get())
                .hasCauseInstanceOf(IOException.class);
        assertThat(attempts).hasSize(2);
    }

    private static Request request() {
        return Request.builder()
                .putAttachment(
                        RoutingKey.ATTACHMENT_KEY, RoutingKey.of(new RoutingKey.Value(RoutingKey.Type.STRING, "table")))
                .build();
    }
}
