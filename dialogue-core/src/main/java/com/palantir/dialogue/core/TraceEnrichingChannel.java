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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.TraceMetadata;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.SpanType;
import com.palantir.tracing.api.TraceHttpHeaders;
import java.io.IOException;

/** A channel that adds Zipkin compatible tracing headers. */
enum TraceEnrichingChannel implements Filter {
    INSTANCE;

    private static final String OPERATION = "Dialogue-http-request";
    private static final String INITIAL = OPERATION + " initial";

    // TODO(dfox): neverthrow?
    @Override
    public ListenableFuture<Response> executeFilter(Endpoint endpoint, Request request, Channel next) {
        if (Tracer.hasTraceId() && !Tracer.isTraceObservable()) {
            // in the vast majority of cases, we're not actually sampling span information at all, so we might as
            // well save the CPU cycles of creating a DetachedSpan and just send the headers.
            Request req2 = augmentRequest(request);
            return next.execute(endpoint, req2);
        }

        DetachedSpan span = DetachedSpan.start(OPERATION);
        // n.b. This span is required to apply tracing thread state to an initial request. Otherwise if there is
        // no active trace, the detached span would not be associated with work initiated by delegateFactory.
        try (CloseableSpan ignored = span.childSpan(INITIAL, SpanType.CLIENT_OUTGOING)) {
            Request req2 = augmentRequest(request);
            ListenableFuture<Response> future = next.execute(endpoint, req2);
            return DialogueFutures.addDirectListener(future, span::complete);
        }
    }

    @Override
    public Response executeFilter(Endpoint endpoint, Request request, BlockingChannel next) throws IOException {
        try (CloseableTracer ignored = CloseableTracer.startSpan(OPERATION, SpanType.CLIENT_OUTGOING)) {
            Request req2 = augmentRequest(request);
            return next.execute(endpoint, req2);
        }
    }

    private static Request augmentRequest(Request request) {
        TraceMetadata metadata = Tracer.maybeGetTraceMetadata().get();

        Request.Builder tracedRequest = Request.builder()
                .from(request)
                .putHeaderParams(TraceHttpHeaders.TRACE_ID, Tracer.getTraceId())
                .putHeaderParams(TraceHttpHeaders.SPAN_ID, metadata.getSpanId())
                .putHeaderParams(TraceHttpHeaders.IS_SAMPLED, Tracer.isTraceObservable() ? "1" : "0");

        if (metadata.getParentSpanId().isPresent()) {
            tracedRequest.putHeaderParams(
                    TraceHttpHeaders.PARENT_SPAN_ID, metadata.getParentSpanId().get());
        }

        if (metadata.getOriginatingSpanId().isPresent()) {
            tracedRequest.putHeaderParams(
                    TraceHttpHeaders.ORIGINATING_SPAN_ID,
                    metadata.getOriginatingSpanId().get());
        }

        return tracedRequest.build();
    }

    @Override
    public String toString() {
        return "TracedRequestChannel{}";
    }
}
