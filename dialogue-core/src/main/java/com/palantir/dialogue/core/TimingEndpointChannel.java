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

import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

final class TimingEndpointChannel implements EndpointChannel {
    private final EndpointChannel delegate;
    private final boolean isRetryable;
    private final Timer successfulResponseTimer;
    private final Timer preventableFailureResponseTimer;
    private final Timer otherFailureResponseTimer;
    private final Ticker ticker;

    TimingEndpointChannel(
            EndpointChannel delegate,
            Ticker ticker,
            TaggedMetricRegistry taggedMetrics,
            String channelName,
            Endpoint endpoint,
            boolean isEndpointRetryable) {
        this.delegate = delegate;
        this.isRetryable = isEndpointRetryable;
        this.ticker = ticker;
        ClientMetrics metrics = ClientMetrics.of(taggedMetrics);
        this.successfulResponseTimer = metrics.response()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .status("success")
                .build();
        this.preventableFailureResponseTimer = metrics.response()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .status("preventable_failure")
                .build();
        this.otherFailureResponseTimer = metrics.response()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .status("other_failure")
                .build();
    }

    static EndpointChannel create(Config cf, EndpointChannel delegate, Endpoint endpoint) {
        return new TimingEndpointChannel(
                delegate,
                cf.ticker(),
                cf.clientConf().taggedMetricRegistry(),
                cf.channelName(),
                endpoint,
                Endpoints.safeToRetry(endpoint));
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        long beforeNanos = ticker.read();
        ListenableFuture<Response> response = delegate.execute(request);

        return DialogueFutures.addDirectCallback(response, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response response) {
                Timer toUpdate;
                if (Responses.isQosStatus(response)) {
                    toUpdate = preventableFailureResponseTimer;
                } else if (Responses.isInternalServerError(response)) {
                    toUpdate = wasRetried() ? preventableFailureResponseTimer : otherFailureResponseTimer;
                } else {
                    toUpdate = successfulResponseTimer;
                }

                updateTimer(toUpdate);
            }

            @Override
            public void onFailure(Throwable throwable) {
                if (throwable instanceof IOException) {
                    updateTimer(preventableFailureResponseTimer);
                } else {
                    updateTimer(otherFailureResponseTimer);
                }
            }

            private void updateTimer(Timer timer) {
                timer.update(ticker.read() - beforeNanos, TimeUnit.NANOSECONDS);
            }
        });
    }

    private boolean wasRetried() {
        return isRetryable;
    }

    @Override
    public String toString() {
        return "TimingEndpointChannel{" + delegate + '}';
    }
}
