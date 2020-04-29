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
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.logsafe.Safe;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;

public final class BasicBuilder {
    private final ImmutableConfig.Builder builder = ImmutableConfig.builder();

    BasicBuilder() {}

    /**
     * {@link Safe} loggable name to identify this channel for instrumentation and debugging. While this value
     * does not impact behavior, using a unique value for each channel makes it much easier to monitor and debug
     * the RPC stack.
     */
    public BasicBuilder channelName(@Safe String channelName) {
        builder.channelName(channelName);
        return this;
    }

    public BasicBuilder clientConfiguration(ClientConfiguration value) {
        builder.rawConfig(value);
        return this;
    }

    public BasicBuilder channelFactory(ChannelFactory value) {
        builder.channelFactory(value);
        return this;
    }

    @VisibleForTesting
    BasicBuilder random(Random value) {
        builder.random(value);
        return this;
    }

    @VisibleForTesting
    BasicBuilder scheduler(ScheduledExecutorService value) {
        builder.scheduler(() -> value);
        return this;
    }

    @VisibleForTesting
    BasicBuilder maxQueueSize(int value) {
        builder.maxQueueSize(value);
        return this;
    }

    @VisibleForTesting
    BasicBuilder ticker(Ticker value) {
        builder.ticker(value);
        return this;
    }

    @CheckReturnValue
    public Channel build() {
        Config config = builder.build();
        return Channels.createBasicChannel(config);
    }
}
