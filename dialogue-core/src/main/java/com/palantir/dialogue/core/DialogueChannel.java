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
import com.palantir.logsafe.Safe;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.IntStream;

public final class DialogueChannel implements Channel, EndpointChannelFactory {
    private final EndpointChannelFactory delegate;
    private final Config cf;
    private final StickySessionFactory stickySessionFactory;
    private final ImmutableList<Channel> perHostChannels;

    private DialogueChannel(
            Config cf,
            EndpointChannelFactory delegate,
            StickySessionFactory stickySessionFactory,
            ImmutableList<Channel> perHostChannels) {
        this.cf = cf;
        this.delegate = delegate;
        this.stickySessionFactory = stickySessionFactory;
        this.perHostChannels = perHostChannels;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return delegate.endpoint(endpoint).execute(request);
    }

    @Override
    public EndpointChannel endpoint(Endpoint endpoint) {
        return delegate.endpoint(endpoint);
    }

    public StickySessionFactory stickySessionFactory() {
        return stickySessionFactory;
    }

    public ImmutableList<Channel> perHost() {
        return perHostChannels;
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

        @CheckReturnValue
        public DialogueChannel build() {
            Config cf = builder.build();

            ImmutableList.Builder<LimitedChannel> perUriChannels = ImmutableList.builder();
            for (int uriIndex = 0; uriIndex < cf.clientConf().uris().size(); uriIndex++) {
                final int uriIndexForInstrumentation =
                        cf.overrideSingleHostIndex().orElse(uriIndex);
                String uri = cf.clientConf().uris().get(uriIndex);
                Channel channel = cf.channelFactory()
                        .create(DialogueChannelFactory.ChannelArgs.builder()
                                .uri(uri)
                                .uriIndexForInstrumentation(uriIndexForInstrumentation)
                                .build());
                channel = RetryOtherValidatingChannel.create(cf, channel);
                channel = HostMetricsChannel.create(cf, channel, uri);
                Channel tracingChannel =
                        new TraceEnrichingChannel(channel, DialogueTracing.tracingTags(cf, uriIndexForInstrumentation));
                channel = cf.isConcurrencyLimitingEnabled()
                        ? new ChannelToEndpointChannel(endpoint -> {
                            LimitedChannel limited = ConcurrencyLimitedChannel.createForEndpoint(
                                    tracingChannel, cf.channelName(), uriIndexForInstrumentation, endpoint);
                            return QueuedChannel.create(cf, endpoint, limited);
                        })
                        : tracingChannel;
                LimitedChannel limitedChannel = cf.isConcurrencyLimitingEnabled()
                        ? ConcurrencyLimitedChannel.createForHost(cf, channel, uriIndexForInstrumentation)
                        : new ChannelToLimitedChannelAdapter(channel);
                perUriChannels.add(limitedChannel);
            }
            ImmutableList<LimitedChannel> channels = perUriChannels.build();

            NodeSelectingChannel nodeSelectionChannel = NodeSelectionStrategyChannel.create(cf, channels);

            Channel defaultQueuedChannel = QueuedChannel.create(cf, nodeSelectionChannel);
            Channel queuedChannel = new QueueOverrideChannel(defaultQueuedChannel);

            EndpointChannelFactory channelFactory = endpoint -> {
                EndpointChannel channel = new EndpointChannelAdapter(endpoint, queuedChannel);
                channel = TracedChannel.requestAttempt(cf, channel, endpoint);
                channel = RetryingChannel.create(cf, channel, endpoint);
                channel = UserAgentEndpointChannel.create(
                        channel, endpoint, cf.clientConf().userAgent().get());
                channel = DeprecationWarningChannel.create(cf, channel, endpoint);
                channel = new ContentDecodingChannel(channel);
                channel = TracedChannel.create(cf, channel, endpoint);
                channel = TimingEndpointChannel.create(cf, channel, endpoint);
                channel = new InterruptionChannel(channel);
                return new NeverThrowEndpointChannel(channel); // this must come last as a defensive backstop
            };

            StickySessionFactory stickySessionFactory = DefaultStickySessionFactory.create(() -> {
                LimitedChannel stickyLimitedChannel =
                        StickyConcurrencyLimitedChannel.createForQueueKey(nodeSelectionChannel, cf.channelName());
                Channel queueOverride = QueuedChannel.createForSticky(cf, stickyLimitedChannel);
                return endpoint -> {
                    EndpointChannel endpointChannel = channelFactory.endpoint(endpoint);
                    return (EndpointChannel) request -> {
                        request.attachments().put(QueueAttachments.QUEUE_OVERRIDE, queueOverride);
                        return endpointChannel.execute(request);
                    };
                };
            });

            Meter createMeter = DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry())
                    .create()
                    .clientName(cf.channelName())
                    .clientType("dialogue-channel-non-reloading")
                    .build();
            createMeter.mark();

            ImmutableList<Channel> perHostChannels = IntStream.range(0, channels.size())
                    .mapToObj(index -> new ChannelAndFactory() {
                        @Override
                        public EndpointChannel endpoint(Endpoint endpoint) {
                            EndpointChannel endpointChannel = channelFactory.endpoint(endpoint);
                            return request -> {
                                nodeSelectionChannel.routeToHost(index, request);
                                return endpointChannel.execute(request);
                            };
                        }

                        @Override
                        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
                            nodeSelectionChannel.routeToHost(index, request);
                            return channelFactory.endpoint(endpoint).execute(request);
                        }
                    })
                    .collect(ImmutableList.toImmutableList());

            return new DialogueChannel(cf, channelFactory, stickySessionFactory, perHostChannels);
        }

        /** Does *not* do any clever live-reloading. */
        @CheckReturnValue
        public Channel buildNonLiveReloading() {
            return build();
        }
    }

    interface ChannelAndFactory extends Channel, EndpointChannelFactory {}
}
