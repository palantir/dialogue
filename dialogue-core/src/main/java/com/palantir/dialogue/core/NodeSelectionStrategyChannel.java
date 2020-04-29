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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

@SuppressWarnings("NullAway")
final class NodeSelectionStrategyChannel implements LimitedChannel {
    private static final String NODE_SELECTION_HEADER = "Node-Selection-Strategy";

    private final AtomicReference<ChannelWithStrategy> nodeSelectionStrategy;
    private final AtomicReference<ImmutableList<LimitedChannel>> nodeChannels;
    private final String channelName;
    private final Random random;
    private final Ticker tick;
    private final TaggedMetricRegistry metrics;
    private final NodeSelectionStrategySelector strategySelector;
    private final LimitedChannel delegate;

    NodeSelectionStrategyChannel(
            String channelName,
            Random random,
            Ticker tick,
            TaggedMetricRegistry metrics,
            NodeSelectionStrategySelector strategySelector) {
        this.channelName = channelName;
        this.random = random;
        this.tick = tick;
        this.metrics = metrics;
        this.strategySelector = strategySelector;
        this.nodeChannels = new AtomicReference<>(ImmutableList.of());
        this.nodeSelectionStrategy = new AtomicReference<>(getUpdatedNodeSelectionStrategy(
                null, nodeChannels.get(), strategySelector.getCurrentStrategy(), metrics, random, tick, channelName));
        this.delegate = new SupplierChannel(() -> nodeSelectionStrategy.get().channel());
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        return delegate.maybeExecute(endpoint, request);
    }

    void updateChannels(ImmutableList<LimitedChannel> newChannels) {
        nodeChannels.set(newChannels);
        maybeUpdateNodeSelectionStrategy(strategySelector.setActiveChannels(newChannels));
    }

    private void updateRequestedStrategies(LimitedChannel channel, List<DialogueNodeSelectionStrategy> strategies) {
        maybeUpdateNodeSelectionStrategy(strategySelector.updateChannelStrategy(channel, strategies));
    }

    private void maybeUpdateNodeSelectionStrategy(DialogueNodeSelectionStrategy updatedStrategy) {
        // Quick check to avoid expensive CAS
        if (updatedStrategy.equals(nodeSelectionStrategy.get().strategy())) {
            return;
        }
        nodeSelectionStrategy.getAndUpdate(currentStrategy -> getUpdatedNodeSelectionStrategy(
                currentStrategy.channel(), nodeChannels.get(), updatedStrategy, metrics, random, tick, channelName));
    }

    LimitedChannel wrap(LimitedChannel channel) {
        return new WrapperChannel(channel);
    }

    private static ChannelWithStrategy getUpdatedNodeSelectionStrategy(
            @Nullable LimitedChannel previousNodeSelectionStrategy,
            ImmutableList<LimitedChannel> channels,
            DialogueNodeSelectionStrategy updatedStrategy,
            TaggedMetricRegistry metrics,
            Random random,
            Ticker tick,
            String channelName) {

        if (channels.isEmpty()) {
            return ChannelWithStrategy.of(updatedStrategy, new ZeroUriChannel(channelName));
        }
        if (channels.size() == 1) {
            // no fancy node selection heuristic can save us if our one node goes down
            return ChannelWithStrategy.of(updatedStrategy, channels.get(0));
        }

        switch (updatedStrategy) {
            case PIN_UNTIL_ERROR:
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                DialoguePinuntilerrorMetrics pinuntilerrorMetrics = DialoguePinuntilerrorMetrics.of(metrics);
                // Previously pin until error, so we should preserve our previous location
                if (previousNodeSelectionStrategy instanceof PinUntilErrorNodeSelectionStrategyChannel) {
                    PinUntilErrorNodeSelectionStrategyChannel previousPinUntilError =
                            (PinUntilErrorNodeSelectionStrategyChannel) previousNodeSelectionStrategy;
                    return ChannelWithStrategy.of(
                            updatedStrategy,
                            PinUntilErrorNodeSelectionStrategyChannel.of(
                                    Optional.of(previousPinUntilError.getCurrentChannel()),
                                    updatedStrategy,
                                    channels,
                                    pinuntilerrorMetrics,
                                    random,
                                    channelName));
                }
                return ChannelWithStrategy.of(
                        updatedStrategy,
                        PinUntilErrorNodeSelectionStrategyChannel.of(
                                Optional.empty(),
                                updatedStrategy,
                                channels,
                                pinuntilerrorMetrics,
                                random,
                                channelName));
            case BALANCED:
                // When people ask for 'ROUND_ROBIN', they usually just want something to load balance better.
                // We used to have a naive RoundRobinChannel, then tried RandomSelection and now use this heuristic:
                return ChannelWithStrategy.of(
                        updatedStrategy,
                        new BalancedNodeSelectionStrategyChannel(channels, random, tick, metrics, channelName));
            case UNKNOWN:
        }
        throw new SafeRuntimeException("Unknown NodeSelectionStrategy", SafeArg.of("unknown", updatedStrategy));
    }

    @Value.Immutable
    interface ChannelWithStrategy {
        DialogueNodeSelectionStrategy strategy();

        LimitedChannel channel();

        static ChannelWithStrategy of(DialogueNodeSelectionStrategy strategy, LimitedChannel channel) {
            return ImmutableChannelWithStrategy.builder()
                    .strategy(strategy)
                    .channel(channel)
                    .build();
        }
    }

    private class WrapperChannel implements LimitedChannel {
        private final LimitedChannel delegate;
        private final FutureCallback<Response> callback;

        WrapperChannel(LimitedChannel delegate) {
            this.delegate = delegate;
            this.callback = new FutureCallback<Response>() {

                @Override
                public void onSuccess(Response result) {
                    result.getFirstHeader(NODE_SELECTION_HEADER).ifPresent(this::consumeStrategy);
                }

                @Override
                public void onFailure(Throwable _unused) {}

                private void consumeStrategy(String strategy) {
                    updateRequestedStrategies(delegate, DialogueNodeSelectionStrategy.fromHeader(strategy));
                }
            };
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
            return delegate.maybeExecute(endpoint, request).map(this::wrap);
        }

        private ListenableFuture<Response> wrap(ListenableFuture<Response> response) {
            return DialogueFutures.addDirectCallback(response, callback);
        }
    }
}
