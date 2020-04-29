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

import com.google.common.collect.ImmutableList;
import com.palantir.dialogue.Channel;

final class Dialogue {

    private Dialogue() {}

    static Channel createBasicChannel(Config c) {
        ImmutableList<String> uris = ImmutableList.copyOf(c.clientConf().uris());
        ImmutableList<LimitedChannel> perUriChannels = uris.stream()
                .map(uri -> {
                    Channel channel = c.channelFactory().create(uri);
                    // Instrument inner-most channel with instrumentation channels so that we measure only the
                    // over-the-wire-time
                    channel = new InstrumentedChannel(
                            channel, c.channelName(), c.clientConf().taggedMetricRegistry());
                    channel = new ActiveRequestInstrumentationChannel(
                            channel, c.channelName(), "running", c.clientConf().taggedMetricRegistry());
                    // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
                    channel = new TraceEnrichingChannel(channel);

                    return ConcurrencyLimitedChannel.create(
                            new ChannelToLimitedChannelAdapter(channel),
                            c.clientConf().clientQoS(),
                            c.clientConf().taggedMetricRegistry(),
                            c.channelName(),
                            uris.indexOf(uri));
                })
                .collect(ImmutableList.toImmutableList());

        LimitedChannel nodeSelectionChannel = NodeSelectionStrategies.create(
                perUriChannels,
                c.clientConf().nodeSelectionStrategy(),
                null,
                c.ticker(),
                c.random(),
                c.clientConf().taggedMetricRegistry(),
                c.channelName());

        Channel channel = new QueuedChannel(
                nodeSelectionChannel, c.channelName(), c.clientConf().taggedMetricRegistry(), c.maxQueueSize());
        channel = new TracedChannel(channel, "Dialogue-request-attempt");
        channel = RetryingChannel.create(
                channel, c.channelName(), c.clientConf(), c.scheduler().get(), c.random());
        channel = new UserAgentChannel(channel, c.clientConf().userAgent().get());
        channel = new DeprecationWarningChannel(channel, c.clientConf().taggedMetricRegistry());
        channel = new ContentDecodingChannel(channel);
        channel = new NeverThrowChannel(channel);
        channel = new DialogueTracedRequestChannel(channel);
        channel = new ActiveRequestInstrumentationChannel(
                channel, c.channelName(), "processing", c.clientConf().taggedMetricRegistry());
        return channel;
    }
}
