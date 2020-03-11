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
import com.palantir.tracing.Tracers;

final class TracedEndpointChannel implements EndpointChannel {
    private final EndpointChannel delegate;
    private final String operationName;

    TracedEndpointChannel(EndpointChannel delegate, String operationName) {
        this.delegate = delegate;
        Endpoint endpoint = delegate.endpoint();
        this.operationName = operationName + ": " + endpoint.serviceName() + '.' + endpoint.endpointName();
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        return Tracers.wrapListenableFuture(operationName, () -> delegate.execute(request));
    }

    @Override
    public Endpoint endpoint() {
        return delegate.endpoint();
    }

    @Override
    public String toString() {
        return "TracedEndpointChannel{delegate=" + delegate + ", operationName='" + operationName + "\'}";
    }
}
