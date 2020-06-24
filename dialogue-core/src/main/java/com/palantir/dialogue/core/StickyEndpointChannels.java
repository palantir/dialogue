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
import java.util.Random;
import java.util.function.Supplier;
import javax.annotation.concurrent.ThreadSafe;

/** For internal ski product, where all requests for a 'transaction' should land on one host. */
public final class StickyEndpointChannels {

    private final ImmutableList<EndpointChannelFactory> channels;
    private final BalancedScoreTracker tracker;

    public StickyEndpointChannels(ImmutableList<EndpointChannelFactory> channels) {
        this.channels = channels;
        // this.tracker = new BalancedScoreTracker(channels.size(), SafeThreadLocalRandom.get(), Ticker.systemTicker());
        this.tracker = null;
    }

    @VisibleForTesting
    StickyEndpointChannels(ImmutableList<EndpointChannelFactory> channels, Random random, Ticker ticker) {
        this.channels = channels;
        // this.tracker = new BalancedScoreTracker(channels.size(), random, ticker);
        this.tracker = null;
    }

    public Channel getSticky() {
        return new Sticky(this);
    }

    @ThreadSafe
    private static final class Sticky implements EndpointChannelFactory, Channel {

        private final StickyEndpointChannels parent;
        private final Supplier<ChannelScoreInfo> currentHost;

        private Sticky(StickyEndpointChannels parent) {
            this.parent = parent;
            this.currentHost = Suppliers.memoize(parent.tracker::getSingleBestChannelByScore);
        }

        @Override
        public EndpointChannel endpoint(Endpoint endpoint) {
            ChannelScoreInfo tracker = currentHost.get();
            int hostIndex = tracker.channelIndex();
            EndpointChannel delegate = parent.channels.get(hostIndex).endpoint(endpoint);
            return new ScoreTrackingEndpointChannel(delegate, tracker);
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
            DialogueFutures.addDirectCallback(future, tracker);
            return future;
        }
    }
}
