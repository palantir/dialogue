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

import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;

final class ResponseRateEndpointChannel implements EndpointChannel {
    private final EndpointChannel delegate;
    private final Meter responseMeter;
    private final Meter ioExceptionMeter;

    private ResponseRateEndpointChannel(EndpointChannel delegate, Meter responseMeter, Meter ioExceptionMeter) {
        this.delegate = delegate;
        this.responseMeter = responseMeter;
        this.ioExceptionMeter = ioExceptionMeter;
    }

    static EndpointChannel create(
            TaggedMetricRegistry taggedMetrics, String channelName, EndpointChannel delegate, Endpoint endpoint) {
        ClientMetrics metrics = ClientMetrics.of(taggedMetrics);
        return new ResponseRateEndpointChannel(
                delegate,
                metrics.response(channelName),
                metrics.responseError()
                        .channelName(channelName)
                        .serviceName(endpoint.serviceName())
                        .reason("IOException")
                        .build());
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        ListenableFuture<Response> response = delegate.execute(request);
        return DialogueFutures.addDirectCallback(response, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response _result) {
                responseMeter.mark();
            }

            @Override
            public void onFailure(Throwable throwable) {
                responseMeter.mark();
                if (throwable instanceof IOException) {
                    ioExceptionMeter.mark();
                }
            }
        });
    }

    @Override
    public String toString() {
        return "ResponseRateEndpointChannel{" + delegate + '}';
    }
}
