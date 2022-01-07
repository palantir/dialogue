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
import com.google.common.util.concurrent.RateLimiter;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

final class TimingEndpointChannel implements EndpointChannel {

    private static final SafeLogger log = SafeLoggerFactory.get(TimingEndpointChannel.class);
    private static final RateLimiter unknownThrowableLoggingRateLimiter = RateLimiter.create(1);

    private final EndpointChannel delegate;
    private final Timer successTimer;
    private final Timer failureTimer;
    private final Ticker ticker;

    TimingEndpointChannel(
            EndpointChannel delegate,
            Ticker ticker,
            TaggedMetricRegistry taggedMetrics,
            String channelName,
            Endpoint endpoint) {
        this.delegate = delegate;
        this.ticker = ticker;
        ClientMetrics metrics = ClientMetrics.of(taggedMetrics);
        this.successTimer = metrics.response()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .status("success")
                .build();
        this.failureTimer = metrics.response()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .status("failure")
                .build();
    }

    static EndpointChannel create(Config cf, EndpointChannel delegate, Endpoint endpoint) {
        return new TimingEndpointChannel(
                delegate, cf.ticker(), cf.clientConf().taggedMetricRegistry(), cf.channelName(), endpoint);
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        long beforeNanos = ticker.read();
        ListenableFuture<Response> response = delegate.execute(request);

        return DialogueFutures.addDirectCallback(response, new FutureCallback<Response>() {
            @Override
            @SuppressWarnings("PreferJavaTimeOverload")
            public void onSuccess(Response response) {
                if (Responses.isSuccess(response)) {
                    updateTimer(successTimer);
                } else if (Responses.isQosStatus(response) || Responses.isInternalServerError(response)) {
                    updateTimer(failureTimer);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                if (throwable instanceof IOException) {
                    updateTimer(failureTimer);
                } else {
                    if (unknownThrowableLoggingRateLimiter.tryAcquire()) {
                        log.info(
                                "Unknown failure",
                                SafeArg.of(
                                        "exceptionClass", throwable.getClass().getName()));
                    }
                }
            }

            @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
            private void updateTimer(Timer timer) {
                timer.update(ticker.read() - beforeNanos, TimeUnit.NANOSECONDS);
            }
        });
    }

    @Override
    public String toString() {
        return "TimingEndpointChannel{" + delegate + '}';
    }
}
