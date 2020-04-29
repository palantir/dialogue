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
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DialogueChannel implements Channel {
    private static final Logger log = LoggerFactory.getLogger(DialogueChannel.class);

    private final Channel delegate;

    // we keep around internals purely for live-reloading
    private final Config c;

    private final QueuedChannel queuedChannel; // just so we can process the queue when uris reload
    private final Map<String, LimitedChannel> limitedChannelByUri = new ConcurrentHashMap<>();
    private final AtomicReference<LimitedChannel> nodeSelectionStrategy = new AtomicReference<>();

    private DialogueChannel(Config c) {
        this.c = c;
        this.queuedChannel = new QueuedChannel(
                new SupplierChannel(nodeSelectionStrategy::get),
                c.channelName(),
                c.clientConf().taggedMetricRegistry(),
                c.maxQueueSize());
        updateUris(c.clientConf().uris());

        this.delegate = Dialogue.wrapQueuedChannel(c, queuedChannel);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return delegate.execute(endpoint, request);
    }

    public void updateUris(Collection<String> uris) {
        boolean firstTime = nodeSelectionStrategy.get() == null;
        Set<String> uniqueUris = new HashSet<>(uris);
        // Uris didn't really change so nothing to do
        if (limitedChannelByUri.keySet().equals(uniqueUris) && !firstTime) {
            return;
        }

        infoLogUriUpdate(uris, firstTime);

        Sets.SetView<String> staleUris = Sets.difference(limitedChannelByUri.keySet(), uniqueUris);
        Sets.SetView<String> newUris = Sets.difference(uniqueUris, limitedChannelByUri.keySet());

        staleUris.forEach(limitedChannelByUri::remove);
        newUris.forEach(uri -> {
            LimitedChannel singleUriChannel = Dialogue.createPerUriChannel(c, uri);
            limitedChannelByUri.put(uri, singleUriChannel);
        });

        nodeSelectionStrategy.getAndUpdate(previous -> {
            ImmutableList<LimitedChannel> channels = ImmutableList.copyOf(limitedChannelByUri.values());
            return NodeSelectionStrategies.create(c, channels, previous);
        });

        // some queued requests might be able to make progress on a new uri now
        queuedChannel.schedule();
    }

    private void infoLogUriUpdate(Collection<String> uris, boolean firstTime) {
        if (!limitedChannelByUri.isEmpty() && uris.isEmpty()) {
            log.info(
                    "Updated to zero uris",
                    SafeArg.of("channelName", c.channelName()),
                    SafeArg.of("prevNumUris", limitedChannelByUri.size()));
        }
        if (limitedChannelByUri.isEmpty() && !uris.isEmpty() && !firstTime) {
            log.info(
                    "Updated from zero uris",
                    SafeArg.of("channelName", c.channelName()),
                    SafeArg.of("numUris", uris.size()));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final ImmutableConfig.Builder builder = ImmutableConfig.builder();

        private Builder() {}

        /**
         * {@link Safe} loggable name to identify this channel for instrumentation and debugging. While this value
         * does not impact behavior, using a unique value for each channel makes it much easier to monitor and debug
         * the RPC stack.
         */
        public Builder channelName(@Safe String channelName) {
            builder.channelName(channelName);
            return this;
        }

        public Builder clientConfiguration(ClientConfiguration value) {
            builder.rawConfig(value);
            return this;
        }

        public Builder channelFactory(ChannelFactory value) {
            builder.channelFactory(value);
            return this;
        }

        @VisibleForTesting
        Builder random(Random value) {
            builder.random(value);
            return this;
        }

        @VisibleForTesting
        Builder scheduler(ScheduledExecutorService value) {
            builder.scheduler(() -> value);
            return this;
        }

        @VisibleForTesting
        Builder maxQueueSize(int value) {
            builder.maxQueueSize(value);
            return this;
        }

        @VisibleForTesting
        Builder ticker(Ticker value) {
            builder.ticker(value);
            return this;
        }

        @CheckReturnValue
        public DialogueChannel build() {
            Config config = builder.build();
            return new DialogueChannel(config);
        }

        @CheckReturnValue
        public Channel buildBasic() {
            Config c = builder.build();
            return Dialogue.createBasicChannel(c);
        }
    }
}
