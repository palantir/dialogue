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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.TagTranslator;
import com.palantir.tracing.Tracer;

final class TracedChannel implements EndpointChannel {
    private final EndpointChannel delegate;
    private final String operationName;
    private final String operationNameInitial;
    private final TagTranslator<Response> responseTranslator;
    private final TagTranslator<Throwable> throwableTranslator;

    private TracedChannel(EndpointChannel delegate, String operationName, ImmutableMap<String, String> tags) {
        this.delegate = delegate;
        this.operationName = operationName;
        this.operationNameInitial = operationName + " initial";
        this.responseTranslator = new TagTranslator<Response>() {

            @Override
            public <T> void translate(TagAdapter<T> sink, T target, Response response) {
                int status = response.code();
                sink.tag(target, "outcome", status / 100 == 2 ? "success" : "failure");
                sink.tag(target, tags);
                sink.tag(target, "status", Integer.toString(status));
            }
        };

        this.throwableTranslator = new TagTranslator<Throwable>() {

            @Override
            public <T> void translate(TagAdapter<T> sink, T target, Throwable response) {
                sink.tag(target, "outcome", "failure");
                sink.tag(target, tags);
                sink.tag(target, "cause", response.getClass().getSimpleName());
            }
        };
    }

    static EndpointChannel create(Config cf, EndpointChannel delegate, Endpoint endpoint) {
        return new TracedChannel(delegate, "Dialogue: request", tracingTags(cf, endpoint));
    }

    private static ImmutableMap<String, String> tracingTags(Config cf, Endpoint endpoint) {
        return ImmutableMap.of(
                "endpointService",
                endpoint.serviceName(),
                "endpointName",
                endpoint.endpointName(),
                "channel",
                cf.channelName(),
                "mesh",
                Boolean.toString(cf.mesh() == MeshMode.USE_EXTERNAL_MESH));
    }

    static EndpointChannel requestAttempt(Config cf, EndpointChannel delegate, Endpoint endpoint) {
        return new TracedChannel(delegate, "Dialogue-request-attempt", tracingTags(cf, endpoint));
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        if (Tracer.hasUnobservableTrace()) {
            return delegate.execute(request);
        }
        return executeSampled(request);
    }

    private ListenableFuture<Response> executeSampled(Request request) {
        DetachedSpan span = DetachedSpan.start(operationName);
        try (CloseableSpan ignored = span.childSpan(operationNameInitial)) {
            return DialogueFutures.addDirectCallback(delegate.execute(request), new FutureCallback<Response>() {
                @Override
                public void onSuccess(Response response) {
                    span.complete(responseTranslator, response);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    span.complete(throwableTranslator, throwable);
                }
            });
        } catch (Throwable t) {
            span.complete(throwableTranslator, t);
            throw t;
        }
    }

    @Override
    public String toString() {
        return "TracedChannel{operationName=" + operationName + ", delegate=" + delegate + '}';
    }
}
