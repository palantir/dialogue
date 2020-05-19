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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;

/**
 * A channel that observes metrics about the processed requests and responses.
 * TODO(rfink): Consider renaming since this is no longer the only one doing instrumentation
 */
final class InstrumentedChannel implements Channel {
    private final Channel delegate;
    private final String channelName;
    private final ClientMetrics metrics;

    InstrumentedChannel(Channel delegate, String channelName, TaggedMetricRegistry metrics) {
        this.delegate = delegate;
        this.channelName = channelName;
        this.metrics = ClientMetrics.of(metrics);
    }

    static InstrumentedChannel create(Config cf, Channel delegate) {
        return new InstrumentedChannel(delegate, cf.channelName(), cf.clientConf().taggedMetricRegistry());
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Timer.Context context = metrics.response()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .build()
                .time();
        ListenableFuture<Response> response = delegate.execute(endpoint, request);
        return DialogueFutures.addDirectCallback(response, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response _result) {
                context.stop();
            }

            @Override
            public void onFailure(Throwable throwable) {
                context.stop();
                if (throwable instanceof IOException) {
                    metrics.responseError()
                            .channelName(channelName)
                            .serviceName(endpoint.serviceName())
                            .reason("IOException")
                            .build()
                            .mark();
                }
            }
        });
    }

    @Override
    public String toString() {
        return "InstrumentedChannel{" + delegate + '}';
    }
}
