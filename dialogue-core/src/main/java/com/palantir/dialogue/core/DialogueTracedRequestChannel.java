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
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tracing.Tracers;

final class DialogueTracedRequestChannel implements EndpointChannelFactory {
    private final EndpointChannelFactory delegate;

    DialogueTracedRequestChannel(EndpointChannelFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public EndpointChannel endpoint(Endpoint endpoint) {
        EndpointChannel proceed = delegate.endpoint(endpoint);
        String operationName = "Dialogue: request " + endpoint.serviceName() + "#" + endpoint.endpointName();
        return new TracedEndpointChannel(proceed, operationName);
    }

    @Override
    public String toString() {
        return "DialogueTracedRequestChannel{" + delegate + '}';
    }

    private static final class TracedEndpointChannel implements EndpointChannel {
        private final EndpointChannel proceed;
        private final String operationName;

        private TracedEndpointChannel(EndpointChannel proceed, String operationName) {
            this.proceed = proceed;
            this.operationName = operationName;
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            return Tracers.wrapListenableFuture(operationName, () -> proceed.execute(request));
        }

        @Override
        public String toString() {
            return "TracedEndpointChannel{" + proceed + '}';
        }
    }
}
