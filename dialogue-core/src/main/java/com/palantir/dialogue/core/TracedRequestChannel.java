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
import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.TraceHttpHeaders;

/** A channel that adds Zipkin compatible tracing headers. */
final class TracedRequestChannel implements Channel {

    private final Channel delegate;

    TracedRequestChannel(Channel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Request newRequest = Tracer.maybeGetTraceMetadata()
                .map(metadata -> {
                    Request.Builder requestBuilder = Request.builder()
                            .from(request)
                            .putHeaderParams(TraceHttpHeaders.TRACE_ID, Tracer.getTraceId())
                            .putHeaderParams(TraceHttpHeaders.SPAN_ID, metadata.getSpanId())
                            .putHeaderParams(TraceHttpHeaders.IS_SAMPLED, Tracer.isTraceObservable() ? "1" : "0");

                    if (metadata.getParentSpanId().isPresent()) {
                        requestBuilder.putHeaderParams(
                                TraceHttpHeaders.PARENT_SPAN_ID,
                                metadata.getParentSpanId().get());
                    }

                    if (metadata.getOriginatingSpanId().isPresent()) {
                        requestBuilder.putHeaderParams(
                                TraceHttpHeaders.ORIGINATING_SPAN_ID,
                                metadata.getOriginatingSpanId().get());
                    }

                    return requestBuilder.build();
                })
                .orElse(request);

        return delegate.execute(endpoint, newRequest);
    }
}
