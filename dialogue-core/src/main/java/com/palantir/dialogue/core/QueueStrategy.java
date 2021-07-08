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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.dialogue.Channel;
import org.immutables.value.Value;

@Value.Enclosing
final class QueueStrategy {

    private final LoadingCache<ChannelKey, Channel> delegate;

    private QueueStrategy(QueueStrategyFactory factory) {
        this.delegate = Caffeine.newBuilder().build(factory::forChannel);
    }

    public Channel forChannel(ChannelKey channelKey) {
        return delegate.get(channelKey);
    }

    interface QueueStrategyFactory {
        Channel forChannel(ChannelKey channelKey);
    }

    @Value.Immutable
    interface ChannelKey {
        Config config();

        LimitedChannel limitedChannel();
    }

    static QueueStrategy of(QueueStrategyFactory factory) {
        return new QueueStrategy(factory);
    }
}
