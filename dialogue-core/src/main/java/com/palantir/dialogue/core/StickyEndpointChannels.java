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
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.BalancedScoreTracker.ChannelScoreInfo;
import com.palantir.logsafe.Preconditions;
import com.palantir.random.SafeThreadLocalRandom;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** Allows requests for a 'transaction' should land on one host. */
public final class StickyEndpointChannels {

    private final ImmutableList<? extends EndpointChannelFactory> channels;
    private final BalancedScoreTracker tracker;

    private StickyEndpointChannels(Builder params) {
        this.channels = params.channels;
        this.tracker = new BalancedScoreTracker(
                params.channels.size(),
                params.random,
                params.ticker,
                Preconditions.checkNotNull(params.taggedMetrics, "taggedMetricRegistry"),
                Preconditions.checkNotNull(params.channelName, "channelName"));
    }

    /**
     * Returns the current 'best' channel based on its Balanced score. If the underlying host is completely offline,
     * callers are responsible for getting a new sticky channel by calling this method again, relying on the
     * {@link BalancedScoreTracker} to avoid returning the same broken channel again.
     */
    public Channel getStickyChannel() {
        return new Sticky(channels, tracker);
    }

    @ThreadSafe
    private static final class Sticky implements EndpointChannelFactory, Channel {

        private final ImmutableList<? extends EndpointChannelFactory> channels;
        private final Supplier<ChannelScoreInfo> getSingleBestChannel;

        private Sticky(ImmutableList<? extends EndpointChannelFactory> channels, BalancedScoreTracker tracker) {
            this.channels = channels;
            this.getSingleBestChannel = Suppliers.memoize(tracker::getSingleBestChannelByScore);
        }

        @Override
        public EndpointChannel endpoint(Endpoint endpoint) {
            ChannelScoreInfo channelScoreInfo = getSingleBestChannel.get();
            int hostIndex = channelScoreInfo.channelIndex();
            EndpointChannel delegate = channels.get(hostIndex).endpoint(endpoint);
            return new ScoreTrackingEndpointChannel(delegate, channelScoreInfo);
        }

        /**
         * .
         * @deprecated prefer {@link #endpoint}, as it allows binding work upfront
         */
        @Deprecated
        @Override
        public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
            // TODO(dfox): could we delete this entirely?
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    private static final class ScoreTrackingEndpointChannel implements EndpointChannel {
        private final EndpointChannel delegate;
        private final ChannelScoreInfo tracker;

        ScoreTrackingEndpointChannel(EndpointChannel delegate, ChannelScoreInfo tracker) {
            this.delegate = delegate;
            this.tracker = tracker;
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            tracker.startRequest();
            ListenableFuture<Response> future = delegate.execute(request);
            tracker.observability().markRequestMade();
            DialogueFutures.addDirectCallback(future, tracker);
            return future;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ImmutableList<? extends EndpointChannelFactory> channels = ImmutableList.of();

        @Nullable
        private String channelName;

        @Nullable
        private TaggedMetricRegistry taggedMetrics;

        private Random random = SafeThreadLocalRandom.get();
        private Ticker ticker = Ticker.systemTicker();

        private Builder() {}

        public Builder channels(List<? extends EndpointChannelFactory> chans) {
            this.channels = ImmutableList.copyOf(chans);
            return this;
        }

        public Builder channelName(String value) {
            this.channelName = Preconditions.checkNotNull(value, "channelName");
            return this;
        }

        public Builder taggedMetricRegistry(TaggedMetricRegistry value) {
            this.taggedMetrics = Preconditions.checkNotNull(value, "taggedMetrics");
            return this;
        }

        @VisibleForTesting
        Builder random(Random value) {
            this.random = value;
            return this;
        }

        @VisibleForTesting
        Builder ticker(Ticker value) {
            this.ticker = value;
            return this;
        }

        public StickyEndpointChannels build() {
            return new StickyEndpointChannels(this);
        }
    }
}
