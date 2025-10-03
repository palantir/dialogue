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

import com.codahale.metrics.Meter;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.Refreshable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DialogueChannel implements Channel, EndpointChannelFactory {
    private static final SafeLogger log = SafeLoggerFactory.get(DialogueChannel.class);
    private final EndpointChannelFactory delegate;
    private final Config cf;
    private final Supplier<Channel> stickyChannelSupplier;

    private DialogueChannel(Config cf, EndpointChannelFactory delegate, Supplier<Channel> stickyChannelSupplier) {
        this.cf = cf;
        this.delegate = delegate;
        this.stickyChannelSupplier = stickyChannelSupplier;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return delegate.endpoint(endpoint).execute(request);
    }

    @Override
    public EndpointChannel endpoint(Endpoint endpoint) {
        return delegate.endpoint(endpoint);
    }

    public Supplier<Channel> stickyChannels() {
        return stickyChannelSupplier;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "DialogueChannel@" + Integer.toHexString(System.identityHashCode(this)) + "{channelName="
                + cf.channelName() + ", delegate=" + delegate + '}';
    }

    public static final class Builder {
        private final ImmutableConfig.Builder builder = ImmutableConfig.builder();

        private Builder() {}

        /**
         * {@link Safe} loggable name to identify this channel for instrumentation and debugging. While this value
         * does not impact behavior, using a unique value for each channel makes it much easier to monitor and debug
         * the RPC stack.
         */
        public Builder channelName(@Safe String channelName) {
            builder.channelName(channelName);
            return this;
        }

        public Builder clientConfiguration(ClientConfiguration value) {
            builder.rawConfig(value);
            return this;
        }

        /**
         * Exists for backcompat, prefer {@link #uris( Refreshable)}.
         * @deprecated prefer {@link #uris( Refreshable)}.
         */
        @Deprecated
        public Builder uris(List<TargetUri> value) {
            return uris(Refreshable.only(value));
        }

        public Builder uris(Refreshable<List<TargetUri>> value) {
            builder.uris(value);
            return this;
        }

        /**
         * Please use {@link #factory(DialogueChannelFactory)}.
         * @deprecated prefer {@link #factory(DialogueChannelFactory)}
         */
        @Deprecated
        public Builder channelFactory(ChannelFactory value) {
            return factory(DialogueChannelFactory.from(value));
        }

        public Builder factory(DialogueChannelFactory value) {
            builder.channelFactory(value);
            return this;
        }

        /**
         * Metrics for a channel with a single uri can be attributed to a different hostIndex.
         * Otherwise, metrics from all single-uri channels would be attributed to hostIndex 0, making them misleading.
         */
        public Builder overrideHostIndex(OptionalInt maybeUriIndex) {
            builder.overrideSingleHostIndex(maybeUriIndex);
            return this;
        }

        @VisibleForTesting
        Builder random(Random value) {
            builder.random(value);
            return this;
        }

        @VisibleForTesting
        Builder scheduler(ScheduledExecutorService value) {
            builder.scheduler(value);
            return this;
        }

        @VisibleForTesting
        Builder maxQueueSize(int value) {
            builder.maxQueueSize(value);
            return this;
        }

        @VisibleForTesting
        Builder ticker(Ticker value) {
            builder.ticker(value);
            return this;
        }

        public Builder acceptJsonErrorDeserialization(boolean value) {
            builder.acceptJsonErrorDeserialization(value);
            return this;
        }

        @CheckReturnValue
        public DialogueChannel build() {
            Config cf = builder.build();

            DialogueClientMetrics clientMetrics =
                    DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry());

            Meter reloadMeter = clientMetrics
                    .reload()
                    .clientName(cf.channelName())
                    .clientType("dialogue-channel-non-reloading")
                    .build();

            // Reloading currently forgets channel state (pinned target, channel scores, concurrency limits, etc...)
            // In a future change we should attempt to retain this state for channels that are retained between
            // updates.
            LimitedChannel nodeSelectionChannel =
                    new SupplierChannel(cf.uris().map(new Function<List<TargetUri>, LimitedChannel>() {
                        private final Map<TargetUri, ChannelState> state = new ConcurrentHashMap<>();

                        @Override
                        public LimitedChannel apply(List<TargetUri> targetUris) {
                            // remove state for uris we no longer care about, and create new ChannelStates
                            // for uris we don't know about yet
                            state.keySet().retainAll(targetUris);
                            targetUris.forEach(uri -> state.computeIfAbsent(uri, _uri -> new ChannelState()));

                            reloadMeter.mark();
                            log.info(
                                    "Reloaded channel '{}' targets. (uris: {}, numUris: {}, targets: {}, numTargets:"
                                            + " {})",
                                    SafeArg.of("channel", cf.channelName()),
                                    UnsafeArg.of("uris", cf.clientConf().uris()),
                                    SafeArg.of("numUris", cf.clientConf().uris().size()),
                                    UnsafeArg.of("targets", targetUris),
                                    SafeArg.of("numTargets", targetUris.size()));
                            ImmutableList<LimitedChannel> targetChannels =
                                    createHostChannels(cf, targetUris, Collections.unmodifiableMap(state));
                            return NodeSelectionStrategyChannel.create(cf, targetChannels);
                        }
                    }));

            LimitedChannel stickyValidationChannel = new StickyValidationChannel(nodeSelectionChannel);

            Channel multiHostQueuedChannel = QueuedChannel.create(cf, stickyValidationChannel);
            EndpointChannelFactory channelFactory = createEndpointChannelFactory(multiHostQueuedChannel, cf);

            Supplier<Channel> stickyChannelSupplier =
                    StickyEndpointChannels2.create(cf, stickyValidationChannel, channelFactory);

            Meter createMeter = clientMetrics
                    .create()
                    .clientName(cf.channelName())
                    .clientType("dialogue-channel-non-reloading")
                    .build();
            createMeter.mark();

            return new DialogueChannel(cf, channelFactory, stickyChannelSupplier);
        }

        private static ImmutableList<LimitedChannel> createHostChannels(
                Config cf, List<TargetUri> targetUris, Map<TargetUri, ChannelState> state) {
            ImmutableList.Builder<LimitedChannel> perUriChannels = ImmutableList.builder();
            for (int uriIndex = 0; uriIndex < targetUris.size(); uriIndex++) {
                final int uriIndexForInstrumentation =
                        cf.overrideSingleHostIndex().orElse(uriIndex);
                TargetUri targetUri = targetUris.get(uriIndex);
                Channel channel = cf.channelFactory()
                        .create(DialogueChannelFactory.ChannelArgs.builder()
                                .uri(targetUri.uri())
                                .uriIndexForInstrumentation(uriIndexForInstrumentation)
                                .resolvedAddress(targetUri.resolvedAddress())
                                .build());
                channel = RetryOtherValidatingChannel.create(cf, channel);
                channel = HostMetricsChannel.create(cf, channel, targetUri.uri());
                channel =
                        new TraceEnrichingChannel(channel, DialogueTracing.tracingTags(cf, uriIndexForInstrumentation));
                channel = new DeadlineAdvertisementChannel(
                        channel, cf.clientConf().readTimeout());
                channel = new AcceptJsonErrorDeserializationChannel(channel, cf.acceptJsonErrorDeserialization());

                ChannelState channelState = state.get(targetUri);
                Preconditions.checkNotNull(channelState, "no ChannelState exists for this TargetUri");

                LimitedChannel limitedChannel;
                if (cf.isConcurrencyLimitingEnabled()) {
                    Channel unlimited = channel;
                    EndpointChannelState endpointChannelState = channelState.getState(EndpointChannelState.KEY);
                    channel = new ChannelToEndpointChannel(endpoint -> {
                        if (endpoint.tags().contains("dialogue-disable-endpoint-concurrency-limiting")) {
                            return unlimited;
                        }
                        LimitedChannel limited = ConcurrencyLimitedChannel.createForEndpoint(
                                unlimited,
                                cf.channelName(),
                                uriIndexForInstrumentation,
                                endpoint,
                                endpointChannelState.get(endpoint));
                        // Note that because the queue is recreated when nodes are refreshed, it's critical that
                        // the queue can force at least one request through at a time using the behavior introduced
                        // by https://github.com/palantir/dialogue/pull/2422
                        return QueuedChannel.create(cf, endpoint, limited);
                    });
                    limitedChannel = ConcurrencyLimitedChannel.createForHost(
                            cf, channel, uriIndexForInstrumentation, channelState);
                } else {
                    limitedChannel = new ChannelToLimitedChannelAdapter(channel);
                }

                perUriChannels.add(limitedChannel);
            }
            return perUriChannels.build();
        }

        /**
         * {@link ChannelState} provider for per-endpoint channels like the endpoint concurrency limiter.
         * This object is held in the per-host state, and can be used to look up a {@link ChannelState}
         * scoped to an individual {@link Endpoint}.
         * {@link Endpoint} state is held in a weak-keyed cache, equivalent to the one used in
         * {@link ChannelToEndpointChannel}.
         * {@link Endpoint} objects are usually enums, which will never be garbage collected, however it's possible
         * that callers may build an endpoint instances on a per-call basis, so the weak-keyed map is defensive
         * against short-lived endpoints.
         * We don't use the same map because the {@link ChannelToEndpointChannel} retains full channel state which
         * may or may not be designed to be reused across reloads, and we aim to be more precise with state that is
         * kept across uri changes.
         */
        private record EndpointChannelState(LoadingCache<Endpoint, ChannelState> cache) {
            private static final ChannelState.Key<EndpointChannelState> KEY =
                    new ChannelState.Key<>(EndpointChannelState.class, EndpointChannelState::create);

            ChannelState get(Endpoint endpoint) {
                return cache.get(endpoint);
            }

            private static EndpointChannelState create() {
                return new EndpointChannelState(
                        Caffeine.newBuilder().weakKeys().maximumSize(10_000).build(_key -> new ChannelState()));
            }
        }

        private static EndpointChannelFactory createEndpointChannelFactory(Channel multiHostQueuedChannel, Config cf) {
            Channel queuedChannel = new QueueOverrideChannel(multiHostQueuedChannel);
            return endpoint -> {
                EndpointChannel endpointChannel = new EndpointChannelAdapter(endpoint, queuedChannel);
                EndpointChannel channel = cf.clientConf()
                        .userAgent()
                        .map(userAgent -> UserAgentEndpointChannel.create(endpointChannel, endpoint, userAgent))
                        .orElse(endpointChannel);
                channel = RetryingChannel.create(cf, channel, endpoint);
                channel = DeprecationWarningChannel.create(cf, channel, endpoint);
                channel = ContentDecodingChannel.create(cf, channel, endpoint);
                channel = new RetryAdvertisementChannel(channel);
                channel = new RangeAcceptsIdentityEncodingChannel(channel);
                channel = ContentEncodingChannel.of(channel, endpoint);
                channel = TracedChannel.create(cf, channel, endpoint);
                channel = TimingEndpointChannel.create(cf, channel, endpoint);
                channel = new RequestBodyValidationChannel(channel);
                channel = new InterruptionChannel(channel);
                return new NeverThrowEndpointChannel(channel); // this must come last as a defensive backstop
            };
        }

        /** Does *not* do any clever live-reloading. */
        @CheckReturnValue
        public Channel buildNonLiveReloading() {
            return build();
        }
    }
}
