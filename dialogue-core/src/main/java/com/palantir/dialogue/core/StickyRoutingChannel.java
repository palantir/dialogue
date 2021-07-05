/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingAttachments;
import com.palantir.dialogue.RoutingAttachments.RoutingKey;
import java.time.Duration;
import org.immutables.value.Value;

@Value.Enclosing
final class StickyRoutingChannel implements Channel {

    private final Channel globalQueue;
    private final StickyQueues stickyQueues;

    StickyRoutingChannel(Config cf, Channel globalQueue, LimitedChannel nodeSelectionChannel) {
        this.globalQueue = globalQueue;
        this.stickyQueues = new StickyQueues(cf, nodeSelectionChannel);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        RoutingKey routingKey = request.attachments().getOrDefault(RoutingAttachments.ROUTING_KEY, null);
        if (routingKey == null) {
            return globalQueue.execute(endpoint, request);
        } else {
            return stickyQueues.execute(ImmutableStickyRoutingChannel.QueueKey.of(routingKey), endpoint, request);
        }
    }

    private static final class StickyQueues {

        private final LoadingCache<QueueKey, ExpiringQueue> queues;

        private StickyQueues(Config cf, LimitedChannel delegate) {
            this.queues = Caffeine.newBuilder()
                    .refreshAfterWrite(Duration.ofSeconds(10))
                    .build(new CacheLoader<>() {
                        @Override
                        public ExpiringQueue load(QueueKey key) {
                            LimitedChannel forQueueKey =
                                    StickyConcurrencyLimitedChannel.createForQueueKey(delegate, cf.channelName(), key);
                            return new ExpiringQueue(QueuedChannel.createForSticky(cf, forQueueKey));
                        }

                        @Override
                        public ExpiringQueue reload(QueueKey _key, ExpiringQueue oldValue) {
                            if (oldValue.refresh()) {
                                return oldValue;
                            } else {
                                return null;
                            }
                        }
                    });
        }

        public ListenableFuture<Response> execute(QueueKey queueKey, Endpoint endpoint, Request request) {
            return queues.get(queueKey).channel.execute(endpoint, request);
        }
    }

    @Value.Immutable
    interface QueueKey {
        @Value.Parameter
        RoutingKey routingKey();
    }

    private static final class ExpiringQueue {
        private volatile boolean expired;
        private final QueuedChannel channel;

        private ExpiringQueue(QueuedChannel channel) {
            this.channel = channel;
        }

        boolean refresh() {
            boolean prevExpired = expired;
            expired = channel.isEmpty();
            // two strikes and you're out
            return prevExpired && expired;
        }
    }
}
