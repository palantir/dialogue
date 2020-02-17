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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.concurrent.TimeUnit;

final class HistogramChannel implements Channel {
    private final Simulation simulation;
    private final Histogram histogram;
    private final Channel channel;

    HistogramChannel(Simulation simulation, Channel channel) {
        this.simulation = simulation;
        this.channel = channel;
        histogram = new Histogram(new SlidingTimeWindowArrayReservoir(1, TimeUnit.DAYS, simulation.codahaleClock()));
    }

    /** Unit is nanos. */
    public Histogram getHistogram() {
        return histogram;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        long start = simulation.clock().read();
        ListenableFuture<Response> future = channel.execute(endpoint, request);
        future.addListener(() -> histogram.update(simulation.clock().read() - start), MoreExecutors.directExecutor());
        return future;
    }
}
