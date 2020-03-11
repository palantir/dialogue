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

import com.codahale.metrics.Counter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

final class ActiveRequestInstrumentationEndpointChannel implements EndpointChannel {

    private final String stage;
    private final EndpointChannel delegate;
    private final DialogueClientMetrics metrics;
    private final Counter activeRequestCounter;
    private final Runnable listener;

    ActiveRequestInstrumentationEndpointChannel(
            EndpointChannel delegate, @CompileTimeConstant String stage, DialogueClientMetrics metrics) {
        // The delegate must never be allowed to throw, otherwise the counter may be incremented without
        // being decremented.
        this.delegate = new NeverThrowEndpointChannel(delegate);
        this.stage = stage;
        this.metrics = metrics;
        this.activeRequestCounter = metrics.requestActive()
                .serviceName(delegate.endpoint().serviceName())
                .stage(stage)
                .build();
        this.listener = activeRequestCounter::dec;
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        activeRequestCounter.inc();
        ListenableFuture<Response> result = delegate.execute(request);
        result.addListener(listener, MoreExecutors.directExecutor());
        return result;
    }

    @Override
    public String toString() {
        return "ActiveRequestInstrumentationEndpointChannel{delegate=" + delegate + ", stage=" + stage + '}';
    }

    @Override
    public Endpoint endpoint() {
        return delegate.endpoint();
    }
}
