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
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.BalancedScoreTracker.ScoreTracker;
import com.palantir.random.SafeThreadLocalRandom;
import java.util.function.Supplier;
import javax.annotation.concurrent.ThreadSafe;

/** For internal ski product, where all requests for a 'transaction' should land on one host. */
public final class StickyChannels {

    private final ImmutableList<Channel> channels; // TODO(dfox): List<LimitedChannel>??
    private final BalancedScoreTracker tracker;

    public StickyChannels(ImmutableList<Channel> channels) {
        this.channels = channels;
        this.tracker = new BalancedScoreTracker(channels.size(), SafeThreadLocalRandom.get(), Ticker.systemTicker());
    }

    public Channel getStickyChannel() {
        return new StickyChannel(this);
    }

    @ThreadSafe
    private static final class StickyChannel implements Channel {
        private final Supplier<ScoreTrackingChannel> channel;

        private StickyChannel(StickyChannels parent) {
            this.channel = Suppliers.memoize(() -> {
                ScoreTracker scoreTracker = parent.tracker.getBestHost();
                int hostIndex = scoreTracker.hostIndex();
                Channel delegate = parent.channels.get(hostIndex);

                return new ScoreTrackingChannel(delegate, scoreTracker);
            });
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            return channel.get().execute(endpoint, request);
        }
    }

    private static final class ScoreTrackingChannel implements Channel {
        private final Channel delegate;
        private final ScoreTracker tracker;

        ScoreTrackingChannel(Channel delegate, ScoreTracker tracker) {
            this.delegate = delegate;
            this.tracker = tracker;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            tracker.startRequest();
            ListenableFuture<Response> future = delegate.execute(endpoint, request);
            DialogueFutures.addDirectCallback(future, tracker);
            return future;
        }
    }
}
