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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.Tracer;

final class TracedChannel implements EndpointChannel {
    private final EndpointChannel delegate;
    private final String operationName;
    private final String operationNameInitial;

    private TracedChannel(EndpointChannel delegate, String operationName) {
        this.delegate = delegate;
        this.operationName = operationName;
        this.operationNameInitial = operationName + " initial";
    }

    static EndpointChannel create(Config cf, EndpointChannel delegate, Endpoint endpoint) {
        String operationName =
                "Dialogue: request " + endpoint.serviceName() + "#" + endpoint.endpointName() + meshSuffix(cf.mesh());
        return new TracedChannel(delegate, operationName);
    }

    static EndpointChannel requestAttempt(Config cf, EndpointChannel delegate) {
        return new TracedChannel(delegate, "Dialogue-request-attempt" + meshSuffix(cf.mesh()));
    }

    private static String meshSuffix(MeshMode meshMode) {
        return meshMode == MeshMode.USE_EXTERNAL_MESH ? " (Mesh)" : "";
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
        ListenableFuture<Response> future = null;
        try (CloseableSpan ignored = span.childSpan(operationNameInitial)) {
            future = delegate.execute(request);
        } finally {
            if (future != null) {
                future.addListener(span::complete, DialogueFutures.safeDirectExecutor());
            } else {
                span.complete();
            }
        }
        return future;
    }

    @Override
    public String toString() {
        return "TracedChannel{operationName=" + operationName + ", delegate=" + delegate + '}';
    }
}
