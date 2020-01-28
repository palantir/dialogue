/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.dialogue.Channel;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Collection;
import java.util.List;

public final class Channels {

    private Channels() {}

    public static Channel create(
            Collection<? extends Channel> channels,
            UserAgent userAgent,
            TaggedMetricRegistry metrics) {
        List<LimitedChannel> limitedChannels = channels.stream()
                // Instrument inner-most channel with metrics so that we measure only the over-the-wire-time
                .map(channel -> new InstrumentedChannel(channel, metrics))
                .map(channel -> new DeprecationWarningChannel(channel, metrics))
                .map(channel -> new TracedChannel(channel, "Concurrency-Limited Dialogue Request"))
                .map(TracedRequestChannel::new)
                .map(ConcurrencyLimitedChannel::create)
                .collect(ImmutableList.toImmutableList());

        return new UserAgentChannel(
                new RetryingChannel(new QueuedChannel(
                        new RoundRobinChannel(limitedChannels), DispatcherMetrics.of(metrics))),
                userAgent);
    }
}
