/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

/**
 * A channel that observes metrics about the processed requests and responses.
 * TODO(rfink): Consider renaming since this is no longer the only one doing instrumentation
 */
final class InstrumentedChannel implements Channel {
    private final Channel delegate;
    private final String channelName;
    private final ClientMetrics metrics;

    InstrumentedChannel(Channel delegate, String channelName, ClientMetrics metrics) {
        this.delegate = delegate;
        this.channelName = channelName;
        this.metrics = metrics;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Timer.Context context = metrics.response()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .build()
                .time();
        ListenableFuture<Response> response = delegate.execute(endpoint, request);
        response.addListener(context::stop, MoreExecutors.directExecutor());
        return response;
    }

    @Override
    public String toString() {
        return "InstrumentedChannel{" + delegate + '}';
    }
}
