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
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;

public final class Channels {

    private static final int MAX_REQUESTS_PER_CHANNEL = 256;

    private Channels() {}

    public static Channel create(List<? extends Channel> channels, ClientConfiguration config) {
        preconditions(channels, config);

        VersionedTaggedMetricRegistry taggedMetrics = new VersionedTaggedMetricRegistry(config.taggedMetricRegistry());
        DialogueClientMetrics clientMetrics = DialogueClientMetrics.of(taggedMetrics);

        ImmutableList.Builder<LimitedChannel> limitedChannels = ImmutableList.builder();
        for (int hostIndex = 0; hostIndex < channels.size(); hostIndex++) {
            Channel chan = channels.get(hostIndex);
            // Instrument inner-most channel with metrics so that we measure only the over-the-wire-time
            chan = new InstrumentedChannel(chan, clientMetrics);
            // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
            chan = new TracedRequestChannel(chan);
            chan = new TracedChannel(chan, "Dialogue-http-request");
            LimitedChannel limitedChan = new ChannelToLimitedChannelAdapter(chan);
            limitedChan = concurrencyLimiter(limitedChan, config, hostIndex, channels.size(), taggedMetrics);
            limitedChan = new FixedLimitedChannel(limitedChan, MAX_REQUESTS_PER_CHANNEL, clientMetrics);
            limitedChannels.add(limitedChan);
        }

        LimitedChannel limited = nodeSelectionStrategy(config, limitedChannels.build(), taggedMetrics);
        Channel channel = new LimitedChannelToChannelAdapter(limited);
        channel = new TracedChannel(channel, "Dialogue-request-attempt");
        channel = retryingChannel(config, channel);
        channel = new UserAgentChannel(channel, config.userAgent().get());
        channel = new DeprecationWarningChannel(channel, clientMetrics);
        channel = new ContentDecodingChannel(channel);
        channel = new NeverThrowChannel(channel);
        channel = new TracedChannel(channel, "Dialogue-request");

        return channel;
    }

    private static void preconditions(Collection<? extends Channel> channels, ClientConfiguration config) {
        Preconditions.checkArgument(!channels.isEmpty(), "channels must not be empty");
        Preconditions.checkArgument(config.userAgent().isPresent(), "config.userAgent() must be specified");
        Preconditions.checkArgument(
                config.retryOnSocketException() == ClientConfiguration.RetryOnSocketException.ENABLED,
                "Retries on socket exceptions cannot be disabled without disabling retries entirely.");
    }

    private static Channel retryingChannel(ClientConfiguration config, Channel channel) {
        if (config.maxNumRetries() > 0) {
            return new RetryingChannel(
                    channel,
                    config.maxNumRetries(),
                    config.backoffSlotSize(),
                    config.serverQoS(),
                    config.retryOnTimeout());
        } else {
            return channel;
        }
    }

    private static LimitedChannel nodeSelectionStrategy(
            ClientConfiguration config, List<LimitedChannel> channels, VersionedTaggedMetricRegistry metrics) {
        if (channels.size() == 1) {
            return channels.get(0); // no fancy node selection heuristic can save us if our one node goes down
        }

        switch (config.nodeSelectionStrategy()) {
            case PIN_UNTIL_ERROR:
                return PinUntilErrorChannel.pinUntilError(channels, DialoguePinuntilerrorMetrics.of(metrics));
            case ROUND_ROBIN:
                return new RoundRobinChannel(channels);
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                return PinUntilErrorChannel.pinUntilErrorWithoutReshuffle(
                        channels, DialoguePinuntilerrorMetrics.of(metrics));
        }
        throw new SafeRuntimeException(
                "Unknown NodeSelectionStrategy", SafeArg.of("unknown", config.nodeSelectionStrategy()));
    }

    private static LimitedChannel concurrencyLimiter(
            LimitedChannel channel,
            ClientConfiguration config,
            int hostIndex,
            int numChannels,
            TaggedMetricRegistry metrics) {
        ClientConfiguration.ClientQoS clientQoS = config.clientQoS();
        switch (clientQoS) {
            case ENABLED:
                // switch off instrumentation above 10 hosts to prevent unbounded metric tags (which cost DD $)
                OptionalInt hostIndexMax10 = (numChannels < 10) ? OptionalInt.of(hostIndex) : OptionalInt.empty();
                return ConcurrencyLimitedChannel.create(channel, hostIndexMax10, metrics);
            case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                return channel;
        }
        throw new SafeIllegalStateException(
                "Encountered unknown client QoS configuration", SafeArg.of("ClientQoS", clientQoS));
    }
}
