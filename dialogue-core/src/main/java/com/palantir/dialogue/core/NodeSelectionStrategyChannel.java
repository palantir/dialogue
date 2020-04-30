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
import com.google.common.annotations.VisibleForTesting;
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

    private final FutureCallback<Response> callback = new NodeSelectionCallback();

    private final AtomicReference<NodeSelectionChannel> nodeSelectionStrategy;
    private final NodeSelectionStrategyChooser strategySelector;

    private final String channelName;
    private final Random random;
    private final Ticker tick;
    private final TaggedMetricRegistry metrics;
    private final LimitedChannel delegate;

    @VisibleForTesting
    NodeSelectionStrategyChannel(
            NodeSelectionStrategyChooser strategySelector,
            DialogueNodeSelectionStrategy initialStrategy,
            String channelName,
            Random random,
            Ticker tick,
            TaggedMetricRegistry metrics) {
        this.strategySelector = strategySelector;
        this.channelName = channelName;
        this.random = random;
        this.tick = tick;
        this.metrics = metrics;
        this.nodeSelectionStrategy = new AtomicReference<>(NodeSelectionChannel.builder()
                .strategy(initialStrategy)
                .channel(new ZeroUriNodeSelectionChannel(channelName))
                .build());
        this.delegate = new SupplierChannel(() -> nodeSelectionStrategy.get().channel());
    }

    static NodeSelectionStrategyChannel create(Config cf) {
        return new NodeSelectionStrategyChannel(
                NodeSelectionStrategyChannel::getFirstKnownStrategy,
                DialogueNodeSelectionStrategy.of(cf.clientConf().nodeSelectionStrategy()),
                cf.channelName(),
                cf.random(),
                cf.ticker(),
                cf.clientConf().taggedMetricRegistry());
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        return delegate.maybeExecute(endpoint, request).map(this::wrapWithCallback);
    }

    private ListenableFuture<Response> wrapWithCallback(ListenableFuture<Response> response) {
        return DialogueFutures.addDirectCallback(response, callback);
    }

    void updateChannels(ImmutableList<LimitedChannel> updatedChannels) {
        nodeSelectionStrategy.getAndUpdate(prevChannel -> getUpdatedSelectedChannel(
                prevChannel.channel(), updatedChannels, prevChannel.strategy(), metrics, random, tick, channelName));
    }

    private void updateRequestedStrategies(List<DialogueNodeSelectionStrategy> strategies) {
        Optional<DialogueNodeSelectionStrategy> maybeStrategy = strategySelector.updateAndGet(strategies);
        if (maybeStrategy.isPresent()) {
            DialogueNodeSelectionStrategy strategy = maybeStrategy.get();
            // Quick check to avoid expensive CAS
            if (strategy.equals(nodeSelectionStrategy.get().strategy())) {
                return;
            }
            nodeSelectionStrategy.getAndUpdate(prevChannel -> getUpdatedSelectedChannel(
                    prevChannel.channel(), prevChannel.hostChannels(), strategy, metrics, random, tick, channelName));
        }
    }

    private static NodeSelectionChannel getUpdatedSelectedChannel(
            @Nullable LimitedChannel previousNodeSelectionStrategy,
            ImmutableList<LimitedChannel> channels,
            DialogueNodeSelectionStrategy updatedStrategy,
            TaggedMetricRegistry metrics,
            Random random,
            Ticker tick,
            String channelName) {
        NodeSelectionChannel.Builder channelBuilder =
                NodeSelectionChannel.builder().strategy(updatedStrategy).hostChannels(channels);
        if (channels.isEmpty()) {
            return channelBuilder
                    .channel(new ZeroUriNodeSelectionChannel(channelName))
                    .build();
        }
        if (channels.size() == 1) {
            // no fancy node selection heuristic can save us if our one node goes down
            return channelBuilder.channel(channels.get(0)).build();
        }

        switch (updatedStrategy) {
            case PIN_UNTIL_ERROR:
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                DialoguePinuntilerrorMetrics pinuntilerrorMetrics = DialoguePinuntilerrorMetrics.of(metrics);
                // Previously pin until error, so we should preserve our previous location
                if (previousNodeSelectionStrategy instanceof PinUntilErrorNodeSelectionStrategyChannel) {
                    PinUntilErrorNodeSelectionStrategyChannel previousPinUntilError =
                            (PinUntilErrorNodeSelectionStrategyChannel) previousNodeSelectionStrategy;
                    return channelBuilder
                            .channel(PinUntilErrorNodeSelectionStrategyChannel.of(
                                    Optional.of(previousPinUntilError.getCurrentChannel()),
                                    updatedStrategy,
                                    channels,
                                    pinuntilerrorMetrics,
                                    random,
                                    channelName))
                            .build();
                }
                return channelBuilder
                        .channel(PinUntilErrorNodeSelectionStrategyChannel.of(
                                Optional.empty(), updatedStrategy, channels, pinuntilerrorMetrics, random, channelName))
                        .build();
            case BALANCED:
                // When people ask for 'ROUND_ROBIN', they usually just want something to load balance better.
                // We used to have a naive RoundRobinChannel, then tried RandomSelection and now use this heuristic:
                return channelBuilder
                        .channel(new BalancedNodeSelectionStrategyChannel(channels, random, tick, metrics, channelName))
                        .build();
            case UNKNOWN:
        }
        throw new SafeRuntimeException("Unknown NodeSelectionStrategy", SafeArg.of("unknown", updatedStrategy));
    }

    @VisibleForTesting
    static Optional<DialogueNodeSelectionStrategy> getFirstKnownStrategy(
            List<DialogueNodeSelectionStrategy> strategies) {
        for (DialogueNodeSelectionStrategy strategy : strategies) {
            if (!strategy.equals(DialogueNodeSelectionStrategy.UNKNOWN)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }

    @Value.Immutable
    interface NodeSelectionChannel {
        DialogueNodeSelectionStrategy strategy();

        LimitedChannel channel();

        ImmutableList<LimitedChannel> hostChannels();

        class Builder extends ImmutableNodeSelectionChannel.Builder {}

        static Builder builder() {
            return new Builder();
        }
    }

    private final class NodeSelectionCallback implements FutureCallback<Response> {
        @Override
        public void onSuccess(Response result) {
            result.getFirstHeader(NODE_SELECTION_HEADER).ifPresent(this::consumeStrategy);
        }

        @Override
        public void onFailure(Throwable _unused) {}

        private void consumeStrategy(String strategy) {
            updateRequestedStrategies(DialogueNodeSelectionStrategy.fromHeader(strategy));
        }
    }
}
