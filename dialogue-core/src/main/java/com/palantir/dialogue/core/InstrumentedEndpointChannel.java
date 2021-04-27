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
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides instrumentation at a higher level than individual requests, these metrics provide insight
 * into the caller-perceived performance and success rate, including queued time and retry backoffs.
 * Failure metrics only include failures which are presented to the user.
 */
final class InstrumentedEndpointChannel implements EndpointChannel {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedEndpointChannel.class);
    private static final RateLimiter unknownThrowableLoggingRateLimiter = RateLimiter.create(1);

    private final EndpointChannel delegate;
    private final Consumer<String> failureMarker;
    private final Timer successTimer;
    private final Timer failureTimer;
    private final Ticker ticker;
    private final String channelName;
    private final String endpointService;
    private final String endpointName;

    InstrumentedEndpointChannel(
            EndpointChannel delegate,
            Ticker ticker,
            TaggedMetricRegistry taggedMetrics,
            String channelName,
            Endpoint endpoint) {
        this.delegate = delegate;
        this.ticker = ticker;
        this.channelName = channelName;
        this.endpointService = endpoint.serviceName();
        this.endpointName = endpoint.endpointName();
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
        DialogueClientMetrics clientMetrics = DialogueClientMetrics.of(taggedMetrics);
        this.failureMarker = reason -> clientMetrics
                .requestFailure()
                .channelName(channelName)
                .reason(reason)
                .build()
                .mark();
    }

    static EndpointChannel create(Config cf, EndpointChannel delegate, Endpoint endpoint) {
        return new InstrumentedEndpointChannel(
                delegate, cf.ticker(), cf.clientConf().taggedMetricRegistry(), cf.channelName(), endpoint);
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        long beforeNanos = ticker.read();
        ListenableFuture<Response> response = delegate.execute(request);

        return DialogueFutures.addDirectCallback(response, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response response) {
                if (Responses.isSuccess(response)) {
                    updateTimer(successTimer);
                } else if (Responses.isQosStatus(response)) {
                    recordFailure(Reasons.QOS_RESPONSE);
                    updateTimer(failureTimer);
                } else if (Responses.isServerErrorRange(response)) {
                    recordFailure(Reasons.SERVER_ERROR);
                    updateTimer(failureTimer);
                } else if (Responses.isClientError(response)
                        // Avoid noise from auth failures
                        && !Responses.isClientAuthError(response)) {
                    // Timing is not recorded for client errors
                    recordFailure(Reasons.CLIENT_ERROR);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                recordFailure(Reasons.getReason(throwable));
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

            private void updateTimer(Timer timer) {
                timer.update(ticker.read() - beforeNanos, TimeUnit.NANOSECONDS);
            }
        });
    }

    private void recordFailure(String reason) {
        failureMarker.accept(reason);
        if (log.isInfoEnabled()) {
            log.info(
                    "Request failed",
                    SafeArg.of("channel", channelName),
                    SafeArg.of("endpointService", endpointService),
                    SafeArg.of("endpointName", endpointName),
                    SafeArg.of("reason", reason));
        }
    }

    @Override
    public String toString() {
        return "TimingEndpointChannel{" + delegate + '}';
    }
}
