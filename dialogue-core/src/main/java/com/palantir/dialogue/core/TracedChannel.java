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
    private final TagTranslator<Response> responseTranslator;
    private final TagTranslator<Throwable> throwableTranslator;

    private TracedChannel(EndpointChannel delegate, String operationName, ImmutableMap<String, String> tags) {
        this.delegate = delegate;
        this.operationName = operationName;
        this.responseTranslator = DialogueTracing.responseTranslator(tags);
        this.throwableTranslator = DialogueTracing.failureTranslator(tags);
    }

    static EndpointChannel create(Config cf, EndpointChannel delegate, Endpoint endpoint) {
        String operationName =
                "Dialogue: request " + endpoint.serviceName() + "#" + endpoint.endpointName() + meshSuffix(cf.mesh());
        return new TracedChannel(delegate, operationName, tracingTags(cf, endpoint));
    }

    private static String meshSuffix(MeshMode meshMode) {
        return meshMode == MeshMode.USE_EXTERNAL_MESH ? " (Mesh)" : "";
    }

    private static ImmutableMap<String, String> tracingTags(Config cf, Endpoint endpoint) {
        ImmutableMap<String, String> endpointTags = DialogueTracing.tracingTags(endpoint);
        ImmutableMap<String, String> configTags = DialogueTracing.tracingTags(cf);
        return ImmutableMap.<String, String>builderWithExpectedSize(endpointTags.size() + configTags.size())
                .putAll(endpointTags)
                .putAll(configTags)
                .build();
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
        try (CloseableSpan ignored = span.attach()) {
            return DialogueFutures.addDirectCallback(delegate.execute(request), new FutureCallback<>() {
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
