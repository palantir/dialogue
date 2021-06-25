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
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;

final class BenchmarkTimingEndpointChannel implements EndpointChannel {

    private final EndpointChannel delegate;

    private final Timer globalResponseTimer;
    private final Timer perEndpointChannelTimer;
    private final Ticker ticker;

    BenchmarkTimingEndpointChannel(
            String clientName,
            Endpoint endpoint,
            EndpointChannel delegate,
            Ticker ticker,
            TaggedMetricRegistry taggedMetrics) {
        this.delegate = delegate;
        this.ticker = ticker;
        this.globalResponseTimer = MetricNames.clientGlobalResponseTimer(taggedMetrics);
        this.perEndpointChannelTimer = MetricNames.perClientEndpointResponseTimer(taggedMetrics, clientName, endpoint);
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        long beforeNanos = ticker.read();
        return DialogueFutures.addDirectListener(delegate.execute(request), () -> {
            long duration = ticker.read() - beforeNanos;
            globalResponseTimer.update(duration, TimeUnit.NANOSECONDS);
            perEndpointChannelTimer.update(duration, TimeUnit.NANOSECONDS);
        });
    }

    @Override
    public String toString() {
        return "BenchmarkTimingEndpointChannel{" + delegate + '}';
    }
}
