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
import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.BlockingEndpointChannel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

final class TimingEndpointChannel implements EndpointFilter2 {
    private final Timer responseTimer;
    private final Meter ioExceptionMeter;
    private final Ticker ticker;

    TimingEndpointChannel(Ticker ticker, TaggedMetricRegistry taggedMetrics, String channelName, Endpoint endpoint) {
        this.ticker = ticker;
        ClientMetrics metrics = ClientMetrics.of(taggedMetrics);
        this.responseTimer = metrics.response()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .build();
        this.ioExceptionMeter = metrics.responseError()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .reason("IOException")
                .build();
    }

    static EndpointFilter2 create(Config cf, Endpoint endpoint) {
        return new TimingEndpointChannel(
                cf.ticker(), cf.clientConf().taggedMetricRegistry(), cf.channelName(), endpoint);
    }

    @Override
    public Response executeBlocking(Request request, BlockingEndpointChannel next) throws IOException {
        long beforeNanos = ticker.read();
        try {
            Response response = next.execute(request);
            responseTimer.update(ticker.read() - beforeNanos, TimeUnit.NANOSECONDS);
            return response;
        } catch (RuntimeException | IOException e) {
            responseTimer.update(ticker.read() - beforeNanos, TimeUnit.NANOSECONDS);
            if (e instanceof IOException) {
                ioExceptionMeter.mark();
            }
            throw e;
        }
    }

    @Override
    public ListenableFuture<Response> executeAsync(Request request, EndpointChannel next) {
        long beforeNanos = ticker.read();
        ListenableFuture<Response> response = next.execute(request);

        return DialogueFutures.addDirectCallback(response, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response _result) {
                updateResponseTimer();
            }

            @Override
            public void onFailure(Throwable throwable) {
                updateResponseTimer();
                if (throwable instanceof IOException) {
                    ioExceptionMeter.mark();
                }
            }

            private void updateResponseTimer() {
                responseTimer.update(ticker.read() - beforeNanos, TimeUnit.NANOSECONDS);
            }
        });
    }

    @Override
    public String toString() {
        return "TimingEndpointChannel{}";
    }
}
