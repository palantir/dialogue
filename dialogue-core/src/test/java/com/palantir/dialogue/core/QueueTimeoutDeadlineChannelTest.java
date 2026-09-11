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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.deadlines.Deadlines;
import com.palantir.deadlines.Deadlines.Enforcement;
import com.palantir.deadlines.Deadlines.RequestDecodingAdapter;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.tracing.CloseableTracer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class QueueTimeoutDeadlineChannelTest {

    private final ManualTicker ticker = new ManualTicker();
    private final Queue<SettableFuture<Response>> responses = new ArrayDeque<>();
    private final EndpointChannel delegate = _request -> {
        SettableFuture<Response> response = SettableFuture.create();
        responses.add(response);
        return response;
    };

    @Test
    void reused_request_resolves_a_fresh_deadline_for_each_logical_call() {
        QueueTimeoutDeadlineChannel channel = new QueueTimeoutDeadlineChannel(delegate, ticker, Optional.empty());
        Request request = Request.builder().build();

        ListenableFuture<Response> first;
        try (CloseableTracer ignored = CloseableTracer.startSpan("first")) {
            setDeadline(Duration.ofSeconds(5), Enforcement.ENFORCE);
            first = channel.execute(request);
            assertThat(QueueTimeoutAttachments.getDeadlineExpiration(request))
                    .isBetween(
                            Duration.ofSeconds(4).toNanos(),
                            Duration.ofSeconds(5).toNanos());
        }

        responses.remove().set(new TestResponse().code(200));
        assertThat(first).isDone();
        assertThat(QueueTimeoutAttachments.getDeadlineExpiration(request)).isNull();

        ticker.advance(Duration.ofSeconds(1));
        try (CloseableTracer ignored = CloseableTracer.startSpan("second")) {
            setDeadline(Duration.ofSeconds(10), Enforcement.ENFORCE);
            ListenableFuture<Response> second = channel.execute(request);
            assertThat(second).isNotDone();
            assertThat(QueueTimeoutAttachments.getDeadlineExpiration(request))
                    .isBetween(
                            Duration.ofSeconds(10).toNanos(),
                            Duration.ofSeconds(11).toNanos());
        }
    }

    @Test
    void explicit_disable_overrides_an_enforced_trace_deadline() {
        QueueTimeoutDeadlineChannel channel = new QueueTimeoutDeadlineChannel(delegate, ticker, Optional.of(false));

        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            setDeadline(Duration.ofSeconds(5), Enforcement.ENFORCE);
            Request request = Request.builder().build();
            ListenableFuture<Response> result = channel.execute(request);

            assertThat(result).isNotDone();
            assertThat(QueueTimeoutAttachments.getDeadlineExpiration(request)).isNull();
        }
    }

    @Test
    void explicit_enable_enforces_a_deferred_trace_deadline() {
        QueueTimeoutDeadlineChannel channel = new QueueTimeoutDeadlineChannel(delegate, ticker, Optional.of(true));

        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            setDeadline(Duration.ofSeconds(5), Enforcement.DEFER);
            Request request = Request.builder().build();
            ListenableFuture<Response> result = channel.execute(request);

            assertThat(result).isNotDone();
            assertThat(QueueTimeoutAttachments.getDeadlineExpiration(request))
                    .isBetween(
                            Duration.ofSeconds(4).toNanos(),
                            Duration.ofSeconds(5).toNanos());
        }
    }

    private static void setDeadline(Duration duration, Enforcement enforcement) {
        Deadlines.parseFromRequest(Optional.of(duration), Request.builder().build(), Decoder.INSTANCE, enforcement);
    }

    private enum Decoder implements RequestDecodingAdapter<Request> {
        INSTANCE;

        @Override
        public Optional<String> getFirstHeader(Request request, String headerName) {
            return request.headerParams().get(headerName).stream().findFirst();
        }
    }

    private static final class ManualTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }
}
