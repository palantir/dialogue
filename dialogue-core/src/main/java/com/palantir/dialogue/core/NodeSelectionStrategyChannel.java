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
import com.palantir.dialogue.core.StickyAttachments.StickyTarget;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.immutables.value.Value;

final class NodeSelectionStrategyChannel implements NodeSelectingChannel {
    private static final String NODE_SELECTION_HEADER = "Node-Selection-Strategy";

    private final FutureCallback<Response> callback = new NodeSelectionCallback();
    private final AtomicReference<NodeSelectionChannel> nodeSelectionStrategy = new AtomicReference<>();
    private final NodeSelectionStrategyChooser strategySelector;

    private final String channelName;
    private final Random random;
    private final Ticker tick;
    private final TaggedMetricRegistry metrics;
    private final DialogueNodeselectionMetrics nodeSelectionMetrics;
    private final ImmutableList<LimitedChannel> channels;

    @SuppressWarnings("NullAway")
    private final NodeSelectingChannel delegate =
            new SupplierChannel(() -> nodeSelectionStrategy.get().channel());

    @VisibleForTesting
    NodeSelectionStrategyChannel(
            NodeSelectionStrategyChooser strategySelector,
            DialogueNodeSelectionStrategy initialStrategy,
            String channelName,
            Random random,
            Ticker tick,
            TaggedMetricRegistry metrics,
            ImmutableList<LimitedChannel> channels) {
        this.strategySelector = strategySelector;
        this.channelName = channelName;
        this.random = random;
        this.tick = tick;
        this.metrics = metrics;
        this.nodeSelectionMetrics = DialogueNodeselectionMetrics.of(metrics);
        this.channels = channels;
        this.nodeSelectionStrategy.set(createNodeSelectionChannel(null, initialStrategy));
    }

    static NodeSelectingChannel create(Config cf, ImmutableList<LimitedChannel> channels) {
        if (channels.isEmpty()) {
            return new StickyChannelHandler(new ZeroUriNodeSelectionChannel(cf.channelName()));
        }

        if (channels.size() == 1) {
            return new StickyChannelHandler(new StuckRequestHandler(new NodeSelectingChannel() {

                @Override
                public ImmutableList<LimitedChannel> nodeChannels() {
                    return channels;
                }

                private LimitedChannel delegate = channels.get(0);

                @Override
                public Optional<ListenableFuture<Response>> maybeExecute(
                        Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
                    return delegate.maybeExecute(endpoint, request, limitEnforcement);
                }
            }));
        }

        return new NodeSelectionStrategyChannel(
                NodeSelectionStrategyChannel::getFirstKnownStrategy,
                DialogueNodeSelectionStrategy.of(cf.clientConf().nodeSelectionStrategy()),
                cf.channelName(),
                cf.random(),
                cf.ticker(),
                cf.clientConf().taggedMetricRegistry(),
                channels);
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        Optional<ListenableFuture<Response>> maybe =
                StickyChannelHandler.maybeExecute(delegate, endpoint, request, limitEnforcement);
        if (!maybe.isPresent()) {
            return Optional.empty();
        }

        ListenableFuture<Response> wrappedFuture = DialogueFutures.addDirectCallback(maybe.get(), callback);
        return Optional.of(wrappedFuture);
    }

    private NodeSelectionChannel createNodeSelectionChannel(
            @Nullable LimitedChannel previousNodeSelectionStrategy, DialogueNodeSelectionStrategy strategy) {
        NodeSelectionChannel.Builder channelBuilder =
                NodeSelectionChannel.builder().strategy(strategy);

        switch (strategy) {
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
                                    strategy,
                                    channels,
                                    pinuntilerrorMetrics,
                                    random,
                                    tick,
                                    channelName))
                            .build();
                } else {
                    return channelBuilder
                            .channel(PinUntilErrorNodeSelectionStrategyChannel.of(
                                    Optional.empty(),
                                    strategy,
                                    channels,
                                    pinuntilerrorMetrics,
                                    random,
                                    tick,
                                    channelName))
                            .build();
                }
            case BALANCED:
                // When people ask for 'ROUND_ROBIN', they usually just want something to load balance better.
                // We used to have a naive RoundRobinChannel, then tried RandomSelection and now use this heuristic:
                return channelBuilder
                        .channel(new BalancedNodeSelectionStrategyChannel(channels, random, tick, metrics, channelName))
                        .build();
            case UNKNOWN:
        }
        throw new SafeRuntimeException("Unknown NodeSelectionStrategy", SafeArg.of("unknown", strategy));
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

    @Override
    public String toString() {
        return "NodeSelectionStrategyChannel{" + nodeSelectionStrategy + '}';
    }

    @Override
    public ImmutableList<LimitedChannel> nodeChannels() {
        return delegate.nodeChannels();
    }

    @Value.Immutable
    interface NodeSelectionChannel {
        DialogueNodeSelectionStrategy strategy();

        NodeSelectingChannel channel();

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

        @SuppressWarnings("NullAway")
        private void consumeStrategy(String strategy) {
            Optional<DialogueNodeSelectionStrategy> maybeStrategy =
                    strategySelector.updateAndGet(DialogueNodeSelectionStrategy.fromHeader(strategy));
            if (!maybeStrategy.isPresent()) {
                return;
            }
            DialogueNodeSelectionStrategy fromServer = maybeStrategy.get();

            // Quick check to avoid expensive CAS
            if (fromServer.equals(nodeSelectionStrategy.get().strategy())) {
                return;
            }

            nodeSelectionMetrics
                    .strategy()
                    .channelName(channelName)
                    .strategy(fromServer.toString())
                    .build()
                    .mark();
            nodeSelectionStrategy.getAndUpdate(
                    prevChannel -> createNodeSelectionChannel(prevChannel.channel(), fromServer));
        }
    }

    private static final class StickyChannelHandler implements NodeSelectingChannel {

        private final NodeSelectingChannel delegate;

        StickyChannelHandler(NodeSelectingChannel delegate) {
            this.delegate = delegate;
        }

        static Optional<ListenableFuture<Response>> maybeExecute(
                LimitedChannel channel, Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
            StickyTarget target = request.attachments().getOrDefault(StickyAttachments.STICKY, null);
            if (target != null) {
                return target.maybeExecute(endpoint, request, limitEnforcement);
            }
            return channel.maybeExecute(endpoint, request, limitEnforcement);
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(
                Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
            return maybeExecute(delegate, endpoint, request, limitEnforcement);
        }

        @Override
        public ImmutableList<LimitedChannel> nodeChannels() {
            return delegate.nodeChannels();
        }
    }

    private static final class StuckRequestHandler implements NodeSelectingChannel {

        private final NodeSelectingChannel delegate;

        StuckRequestHandler(NodeSelectingChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(
                Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
            return StickyAttachments.maybeExecute(delegate, endpoint, request, limitEnforcement);
        }

        @Override
        public ImmutableList<LimitedChannel> nodeChannels() {
            return delegate.nodeChannels();
        }
    }
}
