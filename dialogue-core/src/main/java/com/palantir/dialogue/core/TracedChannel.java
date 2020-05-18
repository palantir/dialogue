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
import com.palantir.dialogue.BindEndpoint;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tracing.Tracers;

final class TracedChannel implements Channel2 {
    private final Channel delegate;
    private final String operationName;

    TracedChannel(Channel delegate, String operationName) {
        this.delegate = delegate;
        this.operationName = operationName;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        // TODO(dfox): maybe dedupe with the below version
        return Tracers.wrapListenableFuture(operationName, () -> delegate.execute(endpoint, request));
    }

    @Override
    public String toString() {
        return "TracedChannel{operationName=" + operationName + ", delegate=" + delegate + '}';
    }

    @Override
    public EndpointChannel bindEndpoint(Endpoint endpoint) {
        if (delegate instanceof BindEndpoint) {
            EndpointChannel proceed = ((BindEndpoint) delegate).bindEndpoint(endpoint);
            return new TracedEndpointChannel(proceed);
        } else {
            return req -> execute(endpoint, req);
        }
    }

    private class TracedEndpointChannel implements EndpointChannel {
        private final EndpointChannel proceed;

        TracedEndpointChannel(EndpointChannel proceed) {
            this.proceed = proceed;
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            return Tracers.wrapListenableFuture(operationName, () -> proceed.execute(request));
        }
    }
}
