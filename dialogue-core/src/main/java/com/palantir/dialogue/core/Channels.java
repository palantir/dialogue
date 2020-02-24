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
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class Channels {

    private static final int MAX_REQUESTS_PER_CHANNEL = 256;

    private Channels() {}

    public static Channel create(
            Collection<? extends Channel> channels, UserAgent userAgent, ClientConfiguration config) {
        Preconditions.checkState(!channels.isEmpty(), "channels must not be empty");

        DialogueClientMetrics clientMetrics = DialogueClientMetrics.of(config.taggedMetricRegistry());
        List<CompositeLimitedChannel> limitedChannels = channels.stream()
                // Instrument inner-most channel with metrics so that we measure only the over-the-wire-time
                .map(channel -> new InstrumentedChannel(channel, clientMetrics))
                // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
                .map(TracedRequestChannel::new)
                .map(channel -> new TracedChannel(channel, "Dialogue-http-request"))
                .map(CompositeLimitedChannelAdapter::new)
                .map(concurrencyLimiter(config, clientMetrics))
                .map(channel -> new BlacklistingChannel(channel, Duration.ofNanos(10000)))
                .map(channel -> new FixedLimitedChannel(channel, MAX_REQUESTS_PER_CHANNEL, clientMetrics))
                .collect(ImmutableList.toImmutableList());

        LimitedChannel limited = nodeSelectionStrategy(config, limitedChannels);
        Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(config.taggedMetricRegistry()));
        channel = new TracedChannel(channel, "Dialogue-request-attempt");
        channel = new RetryingChannel(channel, config.maxNumRetries(), config.serverQoS());
        channel = new UserAgentChannel(channel, userAgent);
        channel = new DeprecationWarningChannel(channel, clientMetrics);
        channel = new ContentDecodingChannel(channel);
        channel = new NeverThrowChannel(channel);
        channel = new TracedChannel(channel, "Dialogue-request");

        return channel;
    }

    private static LimitedChannel nodeSelectionStrategy(
            ClientConfiguration config, List<CompositeLimitedChannel> channels) {
        // no fancy node selection heuristic can save us if our one node goes down
        if (channels.size() == 1) {
            return (endpoint, request) ->
                    LimitedResponses.getResponse(channels.get(0).maybeExecute(endpoint, request));
        }

        switch (config.nodeSelectionStrategy()) {
            case PIN_UNTIL_ERROR:
                return PinUntilErrorChannel.pinUntilError(channels);
            case ROUND_ROBIN:
                return new RoundRobinChannel(channels);
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                return PinUntilErrorChannel.pinUntilErrorWithoutReshuffle(channels);
        }
        throw new SafeRuntimeException(
                "Unknown NodeSelectionStrategy", SafeArg.of("unknown", config.nodeSelectionStrategy()));
    }

    private static Function<CompositeLimitedChannel, CompositeLimitedChannel> concurrencyLimiter(
            ClientConfiguration config, DialogueClientMetrics metrics) {
        ClientConfiguration.ClientQoS clientQoS = config.clientQoS();
        switch (clientQoS) {
            case ENABLED:
                return channel -> ConcurrencyLimitedChannel.create(channel, metrics);
            case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                return Function.identity();
        }
        throw new SafeIllegalStateException(
                "Encountered unknown client QoS configuration", SafeArg.of("ClientQoS", clientQoS));
    }
}
