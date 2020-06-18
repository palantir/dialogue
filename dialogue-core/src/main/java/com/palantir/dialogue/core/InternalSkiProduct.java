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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.BalancedScoreTracker.ScoreTracker;
import com.palantir.random.SafeThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

public final class InternalSkiProduct {

    private final BalancedScoreTracker tracker;
    private final ImmutableList<Channel> channels; // TODO(dfox): should probably be List<LimitedChannel>

    public InternalSkiProduct(ImmutableList<Channel> channels) {
        this.channels = channels;
        this.tracker = new BalancedScoreTracker(channels.size(), SafeThreadLocalRandom.get(), Ticker.systemTicker());
    }

    public Channel getStickyChannel() {
        return new StickyChannel(this);
    }

    @NotThreadSafe
    private static final class StickyChannel implements Channel {

        private final InternalSkiProduct parent;
        private final AtomicBoolean firstRequest = new AtomicBoolean(true);
        private final SettableFuture<Channel> stickyChannel = SettableFuture.create();

        @Nullable
        private volatile ScoreTracker scoreTracker;

        private StickyChannel(InternalSkiProduct parent) {
            this.parent = parent;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            if (firstRequest.compareAndSet(true, false)) {
                ScoreTracker tracker = parent.tracker.getChannelsByScore()[0];
                scoreTracker = tracker;

                Channel channel = parent.channels.get(tracker.hostIndex());
                stickyChannel.set(channel);

                return trackRequest(channel, endpoint, request);
            } else {
                return Futures.transformAsync(
                        stickyChannel,
                        channel -> trackRequest(channel, endpoint, request),
                        MoreExecutors.directExecutor());
            }
        }

        private ListenableFuture<Response> trackRequest(Channel channel, Endpoint endpoint, Request request) {
            scoreTracker.startRequest();
            ListenableFuture<Response> future = channel.execute(endpoint, request);
            DialogueFutures.addDirectCallback(future, scoreTracker);
            return future;
        }
    }
}
