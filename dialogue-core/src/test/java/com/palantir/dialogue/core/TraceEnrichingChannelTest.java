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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("FutureReturnValueIgnored") // we verify using mockito
@ExtendWith(MockitoExtension.class)
class TraceEnrichingChannelTest {

    @Mock
    public Channel delegate;

    @Captor
    public ArgumentCaptor<Request> requestCaptor;

    @Test
    void when_span_sampling_is_turned_off_we_still_send_zipkin_headers() {
        Tracer.setSampler(() -> false);
        try (CloseableTracer hello = CloseableTracer.startSpan("hello")) {
            TraceEnrichingChannel channel = new TraceEnrichingChannel(delegate);
            channel.execute(TestEndpoint.POST, Request.builder().build());

            verify(delegate).execute(any(), requestCaptor.capture());
            assertThat(requestCaptor.getValue().headerParams().keySet())
                    .containsExactlyInAnyOrder("X-B3-Sampled", "X-B3-TraceId", "X-B3-SpanId");
        }
    }

    // this is a weird case, but we have to test it!
    @Test
    void when_there_is_no_global_trace_headers_are_still_sent() {
        set200(delegate);

        Tracer.setSampler(() -> true);
        assertThat(Tracer.maybeGetTraceMetadata()).isEmpty();

        TraceEnrichingChannel channel = new TraceEnrichingChannel(delegate);
        channel.execute(TestEndpoint.POST, Request.builder().build());

        verify(delegate).execute(any(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().headerParams().keySet())
                .containsExactlyInAnyOrder(
                        "X-B3-Sampled", "X-B3-TraceId", "X-B3-SpanId", "X-B3-ParentSpanId", "X-OrigSpanId");

        assertThat(Tracer.maybeGetTraceMetadata()).isEmpty();
    }

    private static void set200(Channel chan) {
        when(chan.execute(any(), any())).thenReturn(http(200));
    }

    private static ListenableFuture<Response> http(int value) {
        return Futures.immediateFuture(new TestResponse().code(value));
    }
}
