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
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.Channel;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Channels {
    private static final Logger log = LoggerFactory.getLogger(Channels.class);

    private Channels() {}

    public static Channel create(
            Collection<? extends Channel> channels, UserAgent userAgent, ClientConfiguration config) {
        if (config.nodeSelectionStrategy() != NodeSelectionStrategy.ROUND_ROBIN) {
            log.warn(
                    "Dialogue currently only supports ROUND_ROBIN node selection strategy. {} will be ignored",
                    SafeArg.of("requestedStrategy", config.nodeSelectionStrategy()));
        }
        DialogueClientMetrics clientMetrics = DialogueClientMetrics.of(config.taggedMetricRegistry());
        List<LimitedChannel> limitedChannels = channels.stream()
                // Instrument inner-most channel with metrics so that we measure only the over-the-wire-time
                .map(channel -> new InstrumentedChannel(channel, clientMetrics))
                .map(channel -> new DeprecationWarningChannel(channel, clientMetrics))
                // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
                .map(TracedRequestChannel::new)
                .map(channel -> new TracedChannel(channel, "Concurrency-Limited Dialogue Request"))
                .map(ContentDecodingChannel::new)
                .map(concurrencyLimiter(config))
                .collect(ImmutableList.toImmutableList());

        LimitedChannel limited = new RoundRobinChannel(limitedChannels);
        Channel channel = new QueuedChannel(limited, DispatcherMetrics.of(config.taggedMetricRegistry()));
        channel = new RetryingChannel(channel, config.maxNumRetries());
        channel = new UserAgentChannel(channel, userAgent);
        channel = new NeverThrowChannel(channel);

        return channel;
    }

    private static Function<Channel, LimitedChannel> concurrencyLimiter(ClientConfiguration config) {
        ClientConfiguration.ClientQoS clientQoS = config.clientQoS();
        switch (clientQoS) {
            case ENABLED:
                return ConcurrencyLimitedChannel::create;
            case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                return UnlimitedChannel::new;
        }
        throw new SafeIllegalStateException(
                "Encountered unknown client QoS configuration", SafeArg.of("ClientQoS", clientQoS));
    }
}
