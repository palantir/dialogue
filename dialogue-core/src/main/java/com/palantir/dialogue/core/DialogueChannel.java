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
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.random.SafeThreadLocalRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class DialogueChannel implements Channel {
    private Map<String, LimitedChannel> limitedChannelByUri = new ConcurrentHashMap<>();
    private final AtomicReference<LimitedChannel> nodeSelectionStrategy = new AtomicReference<>();

    private final ClientConfiguration clientConfiguration;
    private final ChannelFactory channelFactory;
    private final Channel delegate;
    private final DialogueClientMetrics clientMetrics;
    private final Ticker clock;
    private final Random random;

    // TODO(forozco): you really want a refreshable of uri separate from the client config
    private DialogueChannel(
            ClientConfiguration clientConfiguration,
            ChannelFactory channelFactory,
            Ticker clock,
            Random random,
            Supplier<ScheduledExecutorService> scheduler) {
        this.clientConfiguration = clientConfiguration;
        this.channelFactory = channelFactory;
        clientMetrics = DialogueClientMetrics.of(clientConfiguration.taggedMetricRegistry());
        this.clock = clock;
        this.random = random;
        updateUris(clientConfiguration.uris());
        this.delegate = wrap(
                new SupplierChannel(nodeSelectionStrategy::get), clientConfiguration, scheduler, random, clientMetrics);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return delegate.execute(endpoint, request);
    }

    public void updateUris(Collection<String> uris) {
        Set<String> uniqueUris = new HashSet<>(uris);
        // Uris didn't really change so nothing to do
        if (limitedChannelByUri.keySet().equals(uniqueUris)) {
            return;
        }

        Sets.SetView<String> staleUris = Sets.difference(limitedChannelByUri.keySet(), uniqueUris);
        Sets.SetView<String> newUris = Sets.difference(uniqueUris, limitedChannelByUri.keySet());

        staleUris.forEach(limitedChannelByUri::remove);
        newUris.forEach(uri -> limitedChannelByUri.put(
                uri, createLimitedChannel(uri, channelFactory, clientConfiguration, clientMetrics, clock)));

        nodeSelectionStrategy.getAndUpdate(previous -> getUpdatedNodeSelectionStrategy(
                previous, clientConfiguration, ImmutableList.copyOf(limitedChannelByUri.values()), random));
    }

    private static LimitedChannel createLimitedChannel(
            String uri,
            ChannelFactory channelFactory,
            ClientConfiguration conf,
            DialogueClientMetrics clientMetrics,
            Ticker clock) {
        Channel channel = channelFactory.create(uri);
        // Instrument inner-most channel with metrics so that we measure only the over-the-wire-time
        channel = new InstrumentedChannel(channel, clientMetrics);
        channel = new ActiveRequestInstrumentationChannel(channel, "running", clientMetrics);
        // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
        channel = new TracedRequestChannel(channel);
        channel = new TracedChannel(channel, "Dialogue-http-request");

        LimitedChannel limitedChannel = new ChannelToLimitedChannelAdapter(channel);
        return concurrencyLimiter(conf, limitedChannel, clientMetrics, clock);
    }

    private static LimitedChannel getUpdatedNodeSelectionStrategy(
            @Nullable LimitedChannel previousNodeSelectionStrategy,
            ClientConfiguration config,
            List<LimitedChannel> channels,
            Random random) {
        if (channels.size() == 1) {
            // no fancy node selection heuristic can save us if our one node goes down
            return channels.get(0);
        }

        switch (config.nodeSelectionStrategy()) {
            case PIN_UNTIL_ERROR:
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                DialoguePinuntilerrorMetrics pinuntilerrorMetrics =
                        DialoguePinuntilerrorMetrics.of(config.taggedMetricRegistry());
                // Previously pin until error, so we should preserve our previous location
                if (previousNodeSelectionStrategy instanceof PinUntilErrorChannel) {
                    PinUntilErrorChannel previousPinUntilError = (PinUntilErrorChannel) previousNodeSelectionStrategy;
                    return PinUntilErrorChannel.from(
                            previousPinUntilError.getCurrentChannel(),
                            config.nodeSelectionStrategy(),
                            channels,
                            pinuntilerrorMetrics,
                            random);
                }
                return PinUntilErrorChannel.of(config.nodeSelectionStrategy(), channels, pinuntilerrorMetrics, random);
            case ROUND_ROBIN:
                // No need to preserve previous state with round robin
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

    private static Channel retryingChannel(
            ClientConfiguration conf, Channel channel, Supplier<ScheduledExecutorService> scheduler, Random random) {
        if (conf.maxNumRetries() == 0) {
            return channel;
        }

        return new RetryingChannel(
                channel,
                conf.taggedMetricRegistry(),
                conf.maxNumRetries(),
                conf.backoffSlotSize(),
                conf.serverQoS(),
                conf.retryOnTimeout(),
                scheduler.get(),
                random::nextDouble);
    }

    private static Channel wrap(
            LimitedChannel delegate,
            ClientConfiguration conf,
            Supplier<ScheduledExecutorService> scheduler,
            Random random,
            DialogueClientMetrics clientMetrics) {
        Channel channel = new LimitedChannelToChannelAdapter(delegate);
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Ticker clock = Ticker.systemTicker();
        private Random random = SafeThreadLocalRandom.get();
        private Supplier<ScheduledExecutorService> scheduler = RetryingChannel.sharedScheduler;

        @Nullable
        private ClientConfiguration config;

        @Nullable
        private ChannelFactory channelFactory;

        public Builder clientConfiguration(ClientConfiguration value) {
            this.config = value;
            return this;
        }

        public Builder channelFactory(ChannelFactory value) {
            this.channelFactory = value;
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
        Builder scheduler(ScheduledExecutorService value) {
            this.scheduler = () -> value;
            return this;
        }

        @CheckReturnValue
        public DialogueChannel build() {
            ClientConfiguration conf = Preconditions.checkNotNull(config, "ClientConfiguration is required");
            ChannelFactory factory = Preconditions.checkNotNull(channelFactory, "ChannelFactory is required");
            preconditions(conf);
            ClientConfiguration cleanedConf = ClientConfiguration.builder()
                    .from(conf)
                    .taggedMetricRegistry(new VersionedTaggedMetricRegistry(conf.taggedMetricRegistry()))
                    .build();
            return new DialogueChannel(cleanedConf, factory, clock, random, scheduler);
        }

        private void preconditions(ClientConfiguration conf) {
            Preconditions.checkArgument(!conf.uris().isEmpty(), "channels must not be empty");
            Preconditions.checkArgument(conf.userAgent().isPresent(), "config.userAgent() must be specified");
            Preconditions.checkArgument(
                    conf.retryOnSocketException() == ClientConfiguration.RetryOnSocketException.ENABLED,
                    "Retries on socket exceptions cannot be disabled without disabling retries entirely.");
        }
    }

    private static final class SupplierChannel implements LimitedChannel {
        private final Supplier<LimitedChannel> channelSupplier;

        SupplierChannel(Supplier<LimitedChannel> channelSupplier) {
            this.channelSupplier = channelSupplier;
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
            LimitedChannel delegate = channelSupplier.get();
            return delegate.maybeExecute(endpoint, request);
        }
    }
}
