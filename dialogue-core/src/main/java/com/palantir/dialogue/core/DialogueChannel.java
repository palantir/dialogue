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
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.random.SafeThreadLocalRandom;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DialogueChannel implements Channel {
    private static final Logger log = LoggerFactory.getLogger(DialogueChannel.class);

    private final Map<String, LimitedChannel> limitedChannelByUri = new ConcurrentHashMap<>();
    private final AtomicReference<LimitedChannel> nodeSelectionStrategy = new AtomicReference<>();
    private final QueuedChannel queuedChannel; // just so we can process the queue when uris reload

    private final String channelName;
    private final ClientConfiguration clientConfiguration;
    private final ChannelFactory channelFactory;
    private final Channel delegate;
    private final ClientMetrics clientMetrics;
    private final DialogueClientMetrics dialogueClientMetrics;
    private final Random random;
    private Ticker ticker;

    // TODO(forozco): you really want a refreshable of uri separate from the client config
    private DialogueChannel(
            String channelName,
            ClientConfiguration clientConfiguration,
            ChannelFactory channelFactory,
            Random random,
            Supplier<ScheduledExecutorService> scheduler,
            int maxQueueSize,
            Ticker ticker) {
        this.channelName = channelName;
        this.clientConfiguration = clientConfiguration;
        this.channelFactory = channelFactory;
        clientMetrics = ClientMetrics.of(clientConfiguration.taggedMetricRegistry());
        dialogueClientMetrics = DialogueClientMetrics.of(clientConfiguration.taggedMetricRegistry());
        this.random = random;
        this.ticker = ticker;
        this.queuedChannel = new QueuedChannel(
                new SupplierChannel(nodeSelectionStrategy::get), channelName, dialogueClientMetrics, maxQueueSize);
        updateUris(clientConfiguration.uris());
        this.delegate = wrap(
                queuedChannel,
                channelName,
                clientConfiguration,
                scheduler,
                random,
                clientMetrics,
                dialogueClientMetrics);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return delegate.execute(endpoint, request);
    }

    public void updateUris(Collection<String> uris) {
        boolean firstTime = nodeSelectionStrategy.get() == null;
        Set<String> uniqueUris = new HashSet<>(uris);
        // Uris didn't really change so nothing to do
        if (limitedChannelByUri.keySet().equals(uniqueUris) && !firstTime) {
            return;
        }

        if (!limitedChannelByUri.isEmpty() && uris.isEmpty()) {
            log.info(
                    "Updated to zero uris",
                    SafeArg.of("channelName", channelName),
                    SafeArg.of("prevNumUris", limitedChannelByUri.size()));
        }
        if (limitedChannelByUri.isEmpty() && !uris.isEmpty() && !firstTime) {
            log.info(
                    "Updated from zero uris",
                    SafeArg.of("channelName", channelName),
                    SafeArg.of("numUris", uris.size()));
        }

        Sets.SetView<String> staleUris = Sets.difference(limitedChannelByUri.keySet(), uniqueUris);
        Sets.SetView<String> newUris = Sets.difference(uniqueUris, limitedChannelByUri.keySet());

        staleUris.forEach(limitedChannelByUri::remove);
        ImmutableList<String> allUris = ImmutableList.<String>builder()
                .addAll(limitedChannelByUri.keySet())
                .addAll(newUris)
                .build();
        newUris.forEach(uri -> limitedChannelByUri.put(uri, createLimitedChannel(uri, allUris.indexOf(uri))));

        nodeSelectionStrategy.getAndUpdate(previous -> getUpdatedNodeSelectionStrategy(
                previous,
                clientConfiguration,
                ImmutableList.copyOf(limitedChannelByUri.values()),
                random,
                ticker,
                channelName));

        // some queued requests might be able to make progress on a new uri now
        queuedChannel.schedule();
    }

    private LimitedChannel createLimitedChannel(String uri, int uriIndex) {
        Channel channel = channelFactory.create(uri);
        // Instrument inner-most channel with instrumentation channels so that we measure only the over-the-wire-time
        channel = new InstrumentedChannel(channel, channelName, clientMetrics);
        channel = new ActiveRequestInstrumentationChannel(channel, channelName, "running", dialogueClientMetrics);
        // TracedChannel must wrap TracedRequestChannel to ensure requests have tracing headers.
        channel = new TraceEnrichingChannel(channel);

        LimitedChannel limitedChannel = new ChannelToLimitedChannelAdapter(channel);
        return concurrencyLimiter(
                clientConfiguration, limitedChannel, clientConfiguration.taggedMetricRegistry(), channelName, uriIndex);
    }

    private static LimitedChannel getUpdatedNodeSelectionStrategy(
            @Nullable LimitedChannel previousNodeSelectionStrategy,
            ClientConfiguration config,
            ImmutableList<LimitedChannel> channels,
            Random random,
            Ticker tick,
            String channelName) {
        if (channels.isEmpty()) {
            return new ZeroUriChannel(channelName);
        }
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
                    return PinUntilErrorChannel.of(
                            Optional.of(previousPinUntilError.getCurrentChannel()),
                            config.nodeSelectionStrategy(),
                            channels,
                            pinuntilerrorMetrics,
                            random,
                            channelName);
                }
                return PinUntilErrorChannel.of(
                        Optional.empty(),
                        config.nodeSelectionStrategy(),
                        channels,
                        pinuntilerrorMetrics,
                        random,
                        channelName);
            case ROUND_ROBIN:
                // When people ask for 'ROUND_ROBIN', they usually just want something to load balance better.
                // We used to have a naive RoundRobinChannel, then tried RandomSelection and now use this heuristic:
                return new BalancedChannel(channels, random, tick, config.taggedMetricRegistry(), channelName);
        }
        throw new SafeRuntimeException(
                "Unknown NodeSelectionStrategy", SafeArg.of("unknown", config.nodeSelectionStrategy()));
    }

    private static LimitedChannel concurrencyLimiter(
            ClientConfiguration config,
            LimitedChannel channel,
            TaggedMetricRegistry metrics,
            String channelName,
            int uriIndex) {
        ClientConfiguration.ClientQoS clientQoS = config.clientQoS();
        switch (clientQoS) {
            case ENABLED:
                return new ConcurrencyLimitedChannel(
                        channel, ConcurrencyLimitedChannel.createLimiter(), channelName, uriIndex, metrics);
            case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                return channel;
        }
        throw new SafeIllegalStateException(
                "Encountered unknown client QoS configuration", SafeArg.of("ClientQoS", clientQoS));
    }

    private static Channel retryingChannel(
            Channel channel,
            String channelName,
            ClientConfiguration conf,
            Supplier<ScheduledExecutorService> scheduler,
            Random random) {
        if (conf.maxNumRetries() == 0) {
            return channel;
        }

        return new RetryingChannel(
                channel,
                channelName,
                conf.taggedMetricRegistry(),
                conf.maxNumRetries(),
                conf.backoffSlotSize(),
                conf.serverQoS(),
                conf.retryOnTimeout(),
                scheduler.get(),
                random::nextDouble);
    }

    private static Channel wrap(
            QueuedChannel queuedChannel,
            String channelName,
            ClientConfiguration conf,
            Supplier<ScheduledExecutorService> scheduler,
            Random random,
            ClientMetrics clientMetrics,
            DialogueClientMetrics dialogueClientMetrics) {
        Channel channel = queuedChannel;
        channel = new TracedChannel(channel, "Dialogue-request-attempt");
        channel = retryingChannel(channel, channelName, conf, scheduler, random);
        channel = new UserAgentChannel(channel, conf.userAgent().get());
        channel = new DeprecationWarningChannel(channel, clientMetrics);
        channel = new ContentDecodingChannel(channel);
        channel = new NeverThrowChannel(channel);
        channel = new DialogueTracedRequestChannel(channel);
        channel = new ActiveRequestInstrumentationChannel(channel, channelName, "processing", dialogueClientMetrics);

        return channel;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Random random = SafeThreadLocalRandom.get();
        private Supplier<ScheduledExecutorService> scheduler = RetryingChannel.sharedScheduler;
        private Ticker ticker = Ticker.systemTicker();

        @Nullable
        private String channelName;

        @Nullable
        private ClientConfiguration config;

        @Nullable
        private ChannelFactory channelFactory;

        private int maxQueueSize = 100_000;

        /**
         * {@link Safe} loggable name to identify this channel for instrumentation and debugging. While this value
         * does not impact behavior, using a unique value for each channel makes it much easier to monitor and debug
         * the RPC stack.
         */
        public Builder channelName(@Safe String value) {
            this.channelName = value;
            return this;
        }

        public Builder clientConfiguration(ClientConfiguration value) {
            this.config = value;
            return this;
        }

        public Builder channelFactory(ChannelFactory value) {
            this.channelFactory = value;
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

        @VisibleForTesting
        Builder maxQueueSize(int value) {
            Preconditions.checkArgument(value > 0, "maxQueueSize must be positive");
            this.maxQueueSize = value;
            return this;
        }

        @VisibleForTesting
        Builder ticker(Ticker value) {
            this.ticker = value;
            return this;
        }

        @CheckReturnValue
        public DialogueChannel build() {
            ClientConfiguration conf = Preconditions.checkNotNull(config, "clientConfiguration is required");
            ChannelFactory factory = Preconditions.checkNotNull(channelFactory, "channelFactory is required");
            String name = Preconditions.checkNotNull(channelName, "channelName is required.");
            preconditions(conf);
            ClientConfiguration cleanedConf = ClientConfiguration.builder()
                    .from(conf)
                    .taggedMetricRegistry(new VersionedTaggedMetricRegistry(conf.taggedMetricRegistry()))
                    .build();
            return new DialogueChannel(name, cleanedConf, factory, random, scheduler, maxQueueSize, ticker);
        }

        private void preconditions(ClientConfiguration conf) {
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
