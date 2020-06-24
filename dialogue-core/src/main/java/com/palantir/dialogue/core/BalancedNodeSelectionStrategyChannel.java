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
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.BalancedScoreTracker.ChannelScoreInfo;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses nodes to achieve the best possible client-side load balancing, by computing a 'score' for each channel and
 * always routing requests to the channel with the lowest score. See {@link BalancedScoreTracker} for details of what
 * goes into a 'score'.
 *
 * This is intended to be a strict improvement over Round Robin and Random Selection which can leave fast servers
 * underutilized, as it sends the same number to both a slow and fast node. It is *not* appropriate for transactional
 * workloads (where n requests must all land on the same server) or scenarios where cache warming is very important.
 * {@link PinUntilErrorNodeSelectionStrategyChannel} remains the best choice for these.
 */
final class BalancedNodeSelectionStrategyChannel implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(BalancedNodeSelectionStrategyChannel.class);

    private final BalancedScoreTracker tracker;
    private final ImmutableList<LimitedChannel> channels;

    BalancedNodeSelectionStrategyChannel(
            ImmutableList<LimitedChannel> channels,
            Random random,
            Ticker ticker,
            TaggedMetricRegistry taggedMetrics,
            String channelName,
            RttSampling samplingEnabled) {
        Preconditions.checkState(channels.size() >= 2, "At least two channels required");
        this.tracker = new BalancedScoreTracker(channels.size(), random, ticker, taggedMetrics, channelName);
        this.channels = channels;
        log.debug("Initialized", SafeArg.of("count", channels.size()), UnsafeArg.of("channels", channels));
    }

    enum RttSampling {
        DEFAULT_OFF,
        ENABLED
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        ChannelScoreInfo[] channelsByScore = tracker.getChannelsByScore();

        for (ChannelScoreInfo channelInfo : channelsByScore) {
            channelInfo.startRequest();

            Optional<ListenableFuture<Response>> maybe =
                    channels.get(channelInfo.channelIndex()).maybeExecute(endpoint, request);

            if (maybe.isPresent()) {
                channelInfo.observability().markRequestMade();
                DialogueFutures.addDirectCallback(maybe.get(), channelInfo);
                return maybe;
            } else {
                channelInfo.undoStartRequest();
            }
        }

        return Optional.empty();
    }

    @VisibleForTesting
    IntStream getScoresForTesting() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public String toString() {
        return "BalancedNodeSelectionStrategyChannel{channels=" + channels + '}';
    }
}
