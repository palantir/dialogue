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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class DialogueChannel implements Channel {
    private final Channel delegate;

    // We store any implementation details that might be useful when live-reloading (they're not actually used to
    // serve requests).
    private final Object nodeSelectionStrategy;

    private DialogueChannel(Channel delegate, Object nodeSelectionStrategy) {
        this.delegate = delegate;
        this.nodeSelectionStrategy = nodeSelectionStrategy;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return delegate.execute(endpoint, request);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static final int MAX_REQUESTS_PER_CHANNEL = 256;

        private Optional<DialogueChannel> maybeExisting = Optional.empty();
        private final List<Channel> channels = new ArrayList<>();
        private Ticker clock = Ticker.systemTicker();
        private Random random = SafeThreadLocalRandom.get();
        private Supplier<ListeningScheduledExecutorService> scheduler = RetryingChannel.sharedScheduler;

        @Nullable
        private ClientConfiguration config;

        public Builder from(DialogueChannel value) {
            this.maybeExisting = Optional.of(value);
            return this;
        }

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
        public DialogueChannel build() {
            ClientConfiguration conf = Preconditions.checkNotNull(config, "ClientConfiguration is required");
            preconditions(channels, conf);

            VersionedTaggedMetricRegistry taggedMetrics =
                    new VersionedTaggedMetricRegistry(conf.taggedMetricRegistry());
            DialogueClientMetrics clientMetrics = DialogueClientMetrics.of(taggedMetrics);

            List<LimitedChannel> limitedChannels = channels.stream()
                    .map(chan -> {
                        LimitedChannel firstInstance = buildSingleUri(chan, clientMetrics, conf, clock);
                        LimitedChannel secondInstance = buildSingleUri(chan, clientMetrics, conf, clock);
                        Preconditions.checkState(
                                firstInstance.equals(secondInstance),
                                "a good equals implementation is necessary for smart live-reloading",
                                UnsafeArg.of("first", firstInstance),
                                UnsafeArg.of("second", secondInstance));
                        return firstInstance; // fine to throw away the secondInstance
                    })
                    .collect(ImmutableList.toImmutableList());

            LimitedChannel nodeSelectionStrategy =
                    nodeSelectionStrategy(limitedChannels, taggedMetrics, conf, maybeExisting, random);
            Channel channel = new LimitedChannelToChannelAdapter(nodeSelectionStrategy);
            channel = new TracedChannel(channel, "Dialogue-request-attempt");
            channel = retryingChannel(channel, conf, scheduler, random);
            channel = new UserAgentChannel(channel, conf.userAgent().get());
            channel = new DeprecationWarningChannel(channel, clientMetrics);
            channel = new ContentDecodingChannel(channel);
            channel = new NeverThrowChannel(channel);
            channel = new TracedChannel(channel, "Dialogue-request");

            return new DialogueChannel(channel, nodeSelectionStrategy);
        }

        private static LimitedChannel buildSingleUri(
                Channel channel, DialogueClientMetrics clientMetrics, ClientConfiguration conf, Ticker clock) {
            // Instrument inner-most channel with metrics so that we measure only the over-the-wire-time
            Channel chan = new InstrumentedChannel(channel, clientMetrics);
            // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
            chan = new TracedRequestChannel(chan);
            chan = new TracedChannel(chan, "Dialogue-http-request");
            LimitedChannel limitedChan = new ChannelToLimitedChannelAdapter(chan);
            limitedChan = concurrencyLimiter(limitedChan, conf, clientMetrics, clock);
            return new FixedLimitedChannel(limitedChan, MAX_REQUESTS_PER_CHANNEL, clientMetrics);
        }

        private static Channel retryingChannel(
                Channel channel,
                ClientConfiguration config,
                Supplier<ListeningScheduledExecutorService> scheduler,
                Random random) {
            if (config.maxNumRetries() > 0) {
                channel = new RetryingChannel(
                        channel,
                        config.maxNumRetries(),
                        config.backoffSlotSize(),
                        config.serverQoS(),
                        config.retryOnTimeout(),
                        scheduler.get(),
                        random::nextDouble);
            }
            return channel;
        }

        private static LimitedChannel concurrencyLimiter(
                LimitedChannel channel, ClientConfiguration config, DialogueClientMetrics metrics, Ticker clock) {
            ClientConfiguration.ClientQoS clientQoS = config.clientQoS();
            switch (clientQoS) {
                case ENABLED:
                    return new ConcurrencyLimitedChannel(
                            channel, ConcurrencyLimitedChannel.createLimiter(clock), metrics);
                case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                    return channel;
            }
            throw new SafeIllegalStateException(
                    "Encountered unknown client QoS configuration", SafeArg.of("ClientQoS", clientQoS));
        }

        private static LimitedChannel nodeSelectionStrategy(
                List<LimitedChannel> channels,
                VersionedTaggedMetricRegistry metrics,
                ClientConfiguration config,
                Optional<DialogueChannel> maybeExisting,
                Random random) {

            if (channels.size() == 1) {
                return channels.get(0); // no fancy node selection heuristic can save us if our one node goes down
            }

            switch (config.nodeSelectionStrategy()) {
                case PIN_UNTIL_ERROR:
                case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                    // create a fresh instance (ignoring the idea of live reloading initially) - we might discard this
                    PinUntilErrorChannel goalInstance = PinUntilErrorChannel.of(
                            config.nodeSelectionStrategy(), channels, DialoguePinuntilerrorMetrics.of(metrics), random);

                    // find out whether we could have live-reloaded to get to the goal state
                    Optional<PinUntilErrorChannel> maybeLiveReloaded = maybeExisting
                            .flatMap(existing -> goalInstance.reloadableFrom(existing.nodeSelectionStrategy))
                            .map(existing -> existing.liveReloadNewInstance(channels));

                    // if we successfully live-reloaded, just discard the newly created 'goalInstance'
                    return maybeLiveReloaded.orElse(goalInstance);
                case ROUND_ROBIN:
                    return new RoundRobinChannel(channels);
            }
            throw new SafeRuntimeException(
                    "Unknown NodeSelectionStrategy", SafeArg.of("unknown", config.nodeSelectionStrategy()));
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
}
