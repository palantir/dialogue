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

import com.codahale.metrics.Clock;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Reservoir;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import org.checkerframework.checker.nullness.qual.Nullable;

final class CostBasedRouting implements LimitedChannel {
    private final ImmutableList<LimitedChannel> channels;
    // Each Reservoir stores the perceived cost of making a request to a channel. We may want to consider bookkeeping at
    // the endpoint level in the future
    private final LoadingCache<LimitedChannel, Reservoir> costPerChannel;

    CostBasedRouting(ImmutableList<LimitedChannel> channels, Clock clock) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.channels = channels;
        costPerChannel = Caffeine.newBuilder()
                .maximumSize(1000)
                .build(upstream -> new ExponentiallyDecayingReservoir(1028, 0.015, clock));
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        List<LimitedChannel> sortedChannelsByCost = sortChannelsByLowestUtilization();
        // TODO(forozco): we'll probably want to tune how we determine the cost of a failure
        long averageCost = (long) sortedChannelsByCost.stream()
                .mapToDouble(
                        channel -> costPerChannel.get(channel).getSnapshot().getMedian())
                .average()
                .getAsDouble();
        for (LimitedChannel channel : sortedChannelsByCost) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            Optional<ListenableFuture<Response>> maybeResponse = channel.maybeExecute(endpoint, request);

            if (maybeResponse.isPresent()) {
                ListenableFuture<Response> response = maybeResponse.get();
                return Optional.of(DialogueFutures.addDirectCallback(
                        response,
                        new CostBasedRoutingCallback(stopwatch, averageCost, costPerChannel.get(channel)::update)));
            }
        }

        return Optional.empty();
    }

    private List<LimitedChannel> sortChannelsByLowestUtilization() {
        return channels.stream()
                // Reading the reservoir so regularly may result in high lock contention
                .sorted(Comparator.comparing(
                        channel -> costPerChannel.get(channel).getSnapshot().getMedian()))
                .collect(ImmutableList.toImmutableList());
    }

    static class CostBasedRoutingCallback implements FutureCallback<Response> {
        private final Stopwatch stopwatch;
        private final long errorCost;
        private final LongConsumer costConsumer;

        CostBasedRoutingCallback(Stopwatch stopwatch, long errorCost, LongConsumer costConsumer) {
            this.stopwatch = stopwatch;
            this.errorCost = errorCost;
            this.costConsumer = costConsumer;
        }

        @Override
        public void onSuccess(@Nullable Response _result) {
            costConsumer.accept(stopwatch.elapsed(TimeUnit.MICROSECONDS));
        }

        @Override
        public void onFailure(Throwable _throwable) {
            costConsumer.accept(errorCost);
        }
    }
}
