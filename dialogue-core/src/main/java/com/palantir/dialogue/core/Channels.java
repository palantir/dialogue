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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.random.SafeThreadLocalRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class Channels {

    private static final int MAX_REQUESTS_PER_CHANNEL = 256;

    private Channels() {}

    public static Channel create(Collection<? extends Channel> channels, ClientConfiguration config) {
        return builder().channels(channels).clientConfiguration(config).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Channel> channels = new ArrayList<>();
        private Ticker clock = Ticker.systemTicker();
        private Random random = SafeThreadLocalRandom.get();
        private Supplier<ListeningScheduledExecutorService> scheduler = RetryingChannel.sharedScheduler;

        @Nullable
        private ClientConfiguration config;

        public Builder channels(Collection<? extends Channel> values) {
            channels.clear();
            channels.addAll(values);
            return this;
        }

        public Builder clientConfiguration(ClientConfiguration value) {
            this.config = value;
            return this;
        }

        @VisibleForTesting
        Builder clock(Ticker value) {
            this.clock = value;
            return this;
        }

        @VisibleForTesting
        Builder random(Random value) {
            this.random = value;
            return this;
        }

        @VisibleForTesting
        Builder scheduler(ListeningScheduledExecutorService value) {
            this.scheduler = () -> value;
            return this;
        }

        @CheckReturnValue
        public Channel build() {
            ClientConfiguration conf = Preconditions.checkNotNull(config, "ClientConfiguration is required");
            preconditions(channels, conf);

            VersionedTaggedMetricRegistry taggedMetrics =
                    new VersionedTaggedMetricRegistry(conf.taggedMetricRegistry());
            DialogueClientMetrics clientMetrics = DialogueClientMetrics.of(taggedMetrics);

            List<LimitedChannel> limitedChannels = channels.stream()
                    // Instrument inner-most channel with metrics so that we measure only the over-the-wire-time
                    .map(channel -> new InstrumentedChannel(channel, clientMetrics))
                    .map(channel -> new ActiveRequestInstrumentationChannel(channel, "in-flight", clientMetrics))
                    // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
                    .map(TracedRequestChannel::new)
                    .map(channel -> new TracedChannel(channel, "Dialogue-http-request"))
                    .map(ChannelToLimitedChannelAdapter::new)
                    .map(channel -> concurrencyLimiter(conf, channel, clientMetrics, clock))
                    .map(channel -> new FixedLimitedChannel(channel, MAX_REQUESTS_PER_CHANNEL, clientMetrics))
                    .collect(ImmutableList.toImmutableList());

            LimitedChannel nodeSelectionStrategy = nodeSelectionStrategy(conf, limitedChannels, taggedMetrics, random);
            Channel channel = new LimitedChannelToChannelAdapter(nodeSelectionStrategy);
            channel = new TracedChannel(channel, "Dialogue-request-attempt");
            channel = retryingChannel(conf, channel, scheduler, random);
            channel = new UserAgentChannel(channel, conf.userAgent().get());
            channel = new DeprecationWarningChannel(channel, clientMetrics);
            channel = new ContentDecodingChannel(channel);
            channel = new NeverThrowChannel(channel);
            channel = new TracedChannel(channel, "Dialogue-request");
            channel = new ActiveRequestInstrumentationChannel(channel, "processing", clientMetrics);

            return channel;
        }

        private static void preconditions(Collection<? extends Channel> channels, ClientConfiguration config) {
            Preconditions.checkNotNull(channels, "Channels is required");

            Preconditions.checkArgument(!channels.isEmpty(), "channels must not be empty");
            Preconditions.checkArgument(config.userAgent().isPresent(), "config.userAgent() must be specified");
            Preconditions.checkArgument(
                    config.retryOnSocketException() == ClientConfiguration.RetryOnSocketException.ENABLED,
                    "Retries on socket exceptions cannot be disabled without disabling retries entirely.");
        }
    }

    private static Channel retryingChannel(
            ClientConfiguration conf,
            Channel channel,
            Supplier<ListeningScheduledExecutorService> scheduler,
            Random random) {
        if (conf.maxNumRetries() == 0) {
            return channel;
        }

        return new RetryingChannel(
                channel,
                conf.maxNumRetries(),
                conf.backoffSlotSize(),
                conf.serverQoS(),
                conf.retryOnTimeout(),
                scheduler.get(),
                random::nextDouble);
    }

    private static LimitedChannel nodeSelectionStrategy(
            ClientConfiguration config,
            List<LimitedChannel> channels,
            VersionedTaggedMetricRegistry metrics,
            Random random) {

        if (channels.size() == 1) {
            return channels.get(0); // no fancy node selection heuristic can save us if our one node goes down
        }

        switch (config.nodeSelectionStrategy()) {
            case PIN_UNTIL_ERROR:
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                return PinUntilErrorChannel.of(
                        config.nodeSelectionStrategy(), channels, DialoguePinuntilerrorMetrics.of(metrics), random);
            case ROUND_ROBIN:
                return new RoundRobinChannel(channels);
        }
        throw new SafeRuntimeException(
                "Unknown NodeSelectionStrategy", SafeArg.of("unknown", config.nodeSelectionStrategy()));
    }

    private static LimitedChannel concurrencyLimiter(
            ClientConfiguration config, LimitedChannel channel, DialogueClientMetrics metrics, Ticker clock) {
        ClientConfiguration.ClientQoS clientQoS = config.clientQoS();
        switch (clientQoS) {
            case ENABLED:
                return new ConcurrencyLimitedChannel(channel, ConcurrencyLimitedChannel.createLimiter(clock), metrics);
            case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                return channel;
        }
        throw new SafeIllegalStateException(
                "Encountered unknown client QoS configuration", SafeArg.of("ClientQoS", clientQoS));
    }
}
