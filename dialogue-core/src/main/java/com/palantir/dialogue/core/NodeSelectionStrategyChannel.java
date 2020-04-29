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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.Nullable;

final class NodeSelectionStrategyChannel implements LimitedChannel {
    private final AtomicReference<LimitedChannel> nodeSelectionStrategy;

    private final NodeSelectionStrategy strategy;
    private final String channelName;
    private final Random random;
    private final Ticker tick;
    private final TaggedMetricRegistry metrics;
    private final LimitedChannel delegate;

    NodeSelectionStrategyChannel(
            NodeSelectionStrategy strategy,
            String channelName,
            Random random,
            Ticker tick,
            TaggedMetricRegistry metrics) {
        this.strategy = strategy;
        this.channelName = channelName;
        this.random = random;
        this.tick = tick;
        this.metrics = metrics;
        this.nodeSelectionStrategy = new AtomicReference<>();
        this.delegate = new SupplierChannel(nodeSelectionStrategy::get);
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        return delegate.maybeExecute(endpoint, request);
    }

    void updateChannels(ImmutableList<LimitedChannel> updatedChannels) {
        nodeSelectionStrategy.getAndUpdate(channel -> getUpdatedNodeSelectionStrategy(
                channel, updatedChannels, strategy, metrics, random, tick, channelName));
    }

    private static LimitedChannel getUpdatedNodeSelectionStrategy(
            @Nullable LimitedChannel previousNodeSelectionStrategy,
            ImmutableList<LimitedChannel> channels,
            NodeSelectionStrategy updatedStrategy,
            TaggedMetricRegistry metrics,
            Random random,
            Ticker tick,
            String channelName) {

        if (channels.isEmpty()) {
            return new ZeroUriNodeSelectionChannel(channelName);
        }

        if (channels.size() == 1) {
            // no fancy node selection heuristic can save us if our one node goes down
            return channels.get(0);
        }

        switch (updatedStrategy) {
            case PIN_UNTIL_ERROR:
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                DialoguePinuntilerrorMetrics pinuntilerrorMetrics = DialoguePinuntilerrorMetrics.of(metrics);
                // Previously pin until error, so we should preserve our previous location
                if (previousNodeSelectionStrategy instanceof PinUntilErrorNodeSelectionStrategyChannel) {
                    PinUntilErrorNodeSelectionStrategyChannel previousPinUntilError =
                            (PinUntilErrorNodeSelectionStrategyChannel) previousNodeSelectionStrategy;
                    return PinUntilErrorNodeSelectionStrategyChannel.of(
                            Optional.of(previousPinUntilError.getCurrentChannel()),
                            updatedStrategy,
                            channels,
                            pinuntilerrorMetrics,
                            random,
                            channelName);
                }
                return PinUntilErrorNodeSelectionStrategyChannel.of(
                        Optional.empty(), updatedStrategy, channels, pinuntilerrorMetrics, random, channelName);
            case ROUND_ROBIN:
                // When people ask for 'ROUND_ROBIN', they usually just want something to load balance better.
                // We used to have a naive RoundRobinChannel, then tried RandomSelection and now use this heuristic:
                return new BalancedNodeSelectionStrategyChannel(channels, random, tick, metrics, channelName);
        }
        throw new SafeRuntimeException("Unknown NodeSelectionStrategy", SafeArg.of("unknown", updatedStrategy));
    }
}
