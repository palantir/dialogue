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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

final class QueueStrategyChannel implements Channel {

    private final QueueStrategy.ChannelKey channelKey;
    private final Channel defaultStrategy;

    QueueStrategyChannel(Config cf, Channel defaultStrategy, LimitedChannel nodeSelectionChannel) {
        this.channelKey = ImmutableQueueStrategy.ChannelKey.builder()
                .config(cf)
                .limitedChannel(nodeSelectionChannel)
                .build();
        this.defaultStrategy = defaultStrategy;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        QueueStrategy queueStrategy = request.attachments().getOrDefault(RoutingAttachments.QUEUE_STRATEGY_KEY, null);
        if (queueStrategy == null) {
            return defaultStrategy.execute(endpoint, request);
        } else {
            return queueStrategy.forChannel(channelKey).execute(endpoint, request);
        }
    }
}
