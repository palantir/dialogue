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

public final class NodeSelectionStrategyChannel implements LimitedChannel {
    private static final String NODE_SELECTION_HEADER = "Node-Selection-Strategy";

    private final AtomicReference<ChannelWithStrategy> nodeSelectionStrategy;
    private final AtomicReference<ImmutableList<LimitedChannel>> nodeChannels;

    private final String channelName;
    private final Random random;
    private final Ticker tick;
    private final TaggedMetricRegistry metrics;
    private final SelectionStrategySelector strategySelector;
    private final LimitedChannel delegate;

    public NodeSelectionStrategyChannel(
            String channelName,
            Random random,
            Ticker tick,
            TaggedMetricRegistry metrics,
            SelectionStrategySelector strategySelector) {
        this.channelName = channelName;
        this.random = random;
        this.tick = tick;
        this.metrics = metrics;
        this.strategySelector = strategySelector;
        this.nodeChannels = new AtomicReference<>(ImmutableList.of());
        this.nodeSelectionStrategy = new AtomicReference<>(getUpdatedNodeSelectionStrategy(
                null, nodeChannels.get(), strategySelector.get(), metrics, random, tick, channelName));
        this.delegate = new SupplierChannel(() -> nodeSelectionStrategy.get().channel());
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        return delegate.maybeExecute(endpoint, request);
    }

    public void updateChannels(List<LimitedChannel> newChannels) {
        ImmutableList<LimitedChannel> wrappedChannels =
                newChannels.stream().map(WrapperChannel::new).collect(ImmutableList.toImmutableList());
        nodeChannels.set(wrappedChannels);
        nodeSelectionStrategy.getAndUpdate(previousChannel -> getUpdatedNodeSelectionStrategy(
                previousChannel, wrappedChannels, previousChannel.strategy(), metrics, random, tick, channelName));
    }

    private void updateStrategy(LimitedChannel channel, String strategy) {
        DialogueNodeSelectionStrategy updatedStrategy = strategySelector.updateAndGet(channel, strategy);
        nodeSelectionStrategy.getAndUpdate(currentStrategy -> {
            if (updatedStrategy.equals(currentStrategy.strategy())) {
                return currentStrategy;
            }
            return getUpdatedNodeSelectionStrategy(
                    currentStrategy, nodeChannels.get(), updatedStrategy, metrics, random, tick, channelName);
        });
    }

    private static ChannelWithStrategy getUpdatedNodeSelectionStrategy(
            @Nullable ChannelWithStrategy previousChannel,
            @Nullable ImmutableList<LimitedChannel> channels,
            DialogueNodeSelectionStrategy updatedStrategy,
            TaggedMetricRegistry metrics,
            Random random,
            Ticker tick,
            String channelName) {
        if (channels.isEmpty()) {
            return ChannelWithStrategy.of(DialogueNodeSelectionStrategy.UNKNOWN, new ZeroUriChannel(channelName));
        }
        if (channels.size() == 1) {
            // no fancy node selection heuristic can save us if our one node goes down
            return ChannelWithStrategy.of(DialogueNodeSelectionStrategy.UNKNOWN, channels.get(0));
        }

        LimitedChannel previousNodeSelectionStrategy = previousChannel.channel();

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
        }
        throw new SafeRuntimeException("Unknown NodeSelectionStrategy", SafeArg.of("unknown", updatedStrategy));
    }

    // TODO(forozco): really you'd want an union
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
                public void onSuccess(@Nullable Response result) {
                    result.getFirstHeader(NODE_SELECTION_HEADER)
                            .ifPresent(strategy -> updateStrategy(delegate, strategy));
                }

                @Override
                public void onFailure(Throwable _unused) {}
            };
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
            return delegate.maybeExecute(endpoint, request)
                    .map(response -> DialogueFutures.addDirectCallback(response, this.callback));
        }
    }
}
