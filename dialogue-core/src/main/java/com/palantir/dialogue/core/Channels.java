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

final class Channels {

    private Channels() {}

    static Channel createBasicChannel(Config c) {
        ImmutableList<LimitedChannel> perUriChannels = c.clientConf().uris().stream()
                .map(uri -> createPerUriChannel(c, uri))
                .collect(ImmutableList.toImmutableList());

        LimitedChannel nodeSelectionChannel = NodeSelectionStrategies.create(c, perUriChannels, null);

        Channel channel = new QueuedChannel(
                nodeSelectionChannel, c.channelName(), c.clientConf().taggedMetricRegistry(), c.maxQueueSize());

        return wrapQueuedChannel(c, channel);
    }

    static LimitedChannel createPerUriChannel(Config cf, String uri) {
        Channel channel = cf.channelFactory().create(uri);
        // Instrument inner-most channel with instrumentation channels so that we measure only the
        // over-the-wire-time
        channel = new InstrumentedChannel(
                channel, cf.channelName(), cf.clientConf().taggedMetricRegistry());
        channel = new ActiveRequestInstrumentationChannel(
                channel, cf.channelName(), "running", cf.clientConf().taggedMetricRegistry());
        // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
        channel = new TraceEnrichingChannel(channel);

        return ConcurrencyLimitedChannel.create(
                new ChannelToLimitedChannelAdapter(channel),
                cf.clientConf().clientQoS(),
                cf.clientConf().taggedMetricRegistry(),
                cf.channelName(),
                cf.clientConf().uris().indexOf(uri));
    }

    static Channel wrapQueuedChannel(Config cf, Channel channel) {
        channel = new TracedChannel(channel, "Dialogue-request-attempt");
        channel = RetryingChannel.create(
                channel, cf.channelName(), cf.clientConf(), cf.scheduler().get(), cf.random());
        channel = new UserAgentChannel(channel, cf.clientConf().userAgent().get());
        channel = new DeprecationWarningChannel(channel, cf.clientConf().taggedMetricRegistry());
        channel = new ContentDecodingChannel(channel);
        channel = new NeverThrowChannel(channel);
        channel = new DialogueTracedRequestChannel(channel);
        channel = new ActiveRequestInstrumentationChannel(
                channel, cf.channelName(), "processing", cf.clientConf().taggedMetricRegistry());
        return channel;
    }
}
