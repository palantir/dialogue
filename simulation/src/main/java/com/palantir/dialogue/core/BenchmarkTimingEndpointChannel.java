/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;

final class BenchmarkTimingEndpointChannel implements EndpointChannel {

    private final EndpointChannel delegate;
    private final Timer responseTimer;
    private final Ticker ticker;

    BenchmarkTimingEndpointChannel(EndpointChannel delegate, Ticker ticker, TaggedMetricRegistry taggedMetrics) {
        this.delegate = delegate;
        this.ticker = ticker;
        this.responseTimer = requestTimer(taggedMetrics);
    }

    static Timer requestTimer(TaggedMetricRegistry taggedMetrics) {
        return taggedMetrics.timer(
                MetricName.builder().safeName("benchmark.reponses").build());
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        long beforeNanos = ticker.read();
        ListenableFuture<Response> response = delegate.execute(request);

        return DialogueFutures.addDirectCallback(response, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response _result) {
                updateResponseTimer();
            }

            @Override
            public void onFailure(Throwable _throwable) {
                updateResponseTimer();
            }

            private void updateResponseTimer() {
                responseTimer.update(ticker.read() - beforeNanos, TimeUnit.NANOSECONDS);
            }
        });
    }

    @Override
    public String toString() {
        return "BenchmarkTimingEndpointChannel{" + delegate + '}';
    }
}
