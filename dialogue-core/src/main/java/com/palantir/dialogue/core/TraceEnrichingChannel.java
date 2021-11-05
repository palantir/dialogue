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

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.TagTranslator;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.Tracers;
import com.palantir.tracing.TracingHeadersEnrichingFunction;
import com.palantir.tracing.api.SpanType;

/** A channel that adds Zipkin compatible tracing headers. */
final class TraceEnrichingChannel implements Channel {
    private static final String OPERATION = "Dialogue-http-request";
    private final NeverThrowChannel delegate; // important to ensure the span is definitely completed!
    private final TagTranslator<Response> responseTranslator;
    private final TagTranslator<Throwable> throwableTranslator;

    TraceEnrichingChannel(Channel delegate, ImmutableMap<String, String> tags) {
        this.delegate = new NeverThrowChannel(delegate);
        this.responseTranslator = DialogueTracing.responseTranslator(tags);
        this.throwableTranslator = DialogueTracing.failureTranslator(tags);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        if (Tracer.hasUnobservableTrace()) {
            // in the vast majority of cases, we're not actually sampling span information at all, so we might as
            // well save the CPU cycles of creating a DetachedSpan and just send the headers.
            return executeInternal(endpoint, request);
        }
        return executeSampled(endpoint, request);
    }

    private ListenableFuture<Response> executeSampled(Endpoint endpoint, Request request) {
        DetachedSpan span = DetachedSpan.start(OPERATION, SpanType.CLIENT_OUTGOING);
        // n.b. This span is required to apply tracing thread state to an initial request. Otherwise if there is
        // no active trace, the detached span would not be associated with work initiated by delegateFactory.
        try (CloseableSpan ignored = span.attach()) {
            ListenableFuture<Response> future = executeInternal(endpoint, request);
            return DialogueFutures.addDirectCallback(future, new FutureCallback<Response>() {
                @Override
                public void onSuccess(Response response) {
                    span.complete(responseTranslator, response);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    span.complete(throwableTranslator, throwable);
                }
            });
        }
    }

    private ListenableFuture<Response> executeInternal(Endpoint endpoint, Request request) {
        Request.Builder requestBuilder = Request.builder().from(request);
        Tracers.addTracingHeaders(requestBuilder, RequestTracingHeadersEnrichingFunction.INSTANCE);
        return delegate.execute(endpoint, requestBuilder.build());
    }

    @Override
    public String toString() {
        return "TracedRequestChannel{" + delegate + '}';
    }

    private enum RequestTracingHeadersEnrichingFunction implements TracingHeadersEnrichingFunction<Request.Builder> {
        INSTANCE;

        @Override
        public void addHeader(String headerName, String headerValue, Request.Builder state) {
            state.putHeaderParams(headerName, headerValue);
        }
    }
}
