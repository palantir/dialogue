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

package com.palantir.dialogue.clients;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfigurationFactory;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.HostEventsSink;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.clients.DialogueClients.PerHostClientFactory;
import com.palantir.dialogue.clients.DialogueClients.StickyChannelFactory;
import com.palantir.dialogue.clients.DialogueClients.StickyChannelFactory2;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.core.StickyEndpointChannels;
import com.palantir.dialogue.hc5.ApacheHttpClientChannels;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.security.Provider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.immutables.value.Value;

final class ReloadingClientFactory implements DialogueClients.ReloadingFactory {
    private final ImmutableReloadingParams params;
    private final ChannelCache cache;

    ReloadingClientFactory(ImmutableReloadingParams params, ChannelCache cache) {
        this.params = params;
        this.cache = cache;
    }

    @Override
    public Channel getNonReloadingChannel(String channelName, ClientConfiguration input) {
        ClientConfiguration clientConf = hydrate(input);
        ApacheHttpClientChannels.ClientBuilder clientBuilder = ApacheHttpClientChannels.clientBuilder()
                .clientConfiguration(clientConf)
                .clientName(channelName);
        params.blockingExecutor().ifPresent(clientBuilder::executor);
        ApacheHttpClientChannels.CloseableClient apacheClient = clientBuilder.build();
        return DialogueChannel.builder()
                .channelName(channelName)
                .clientConfiguration(clientConf)
                .factory(args -> ApacheHttpClientChannels.createSingleUri(args, apacheClient))
                .build();
    }

    @Value.Immutable
    interface ReloadingParams extends AugmentClientConfig {
        Refreshable<ServicesConfigBlock> scb();

        @Value.Default
        default ConjureRuntime runtime() {
            return DefaultConjureRuntime.builder().build();
        }

        Optional<ExecutorService> blockingExecutor();
    }

    @Override
    public <T> T get(Class<T> serviceClass, String serviceName) {
        Channel channel = getChannel(serviceName);
        ConjureRuntime runtime = params.runtime();
        return Reflection.callStaticFactoryMethod(serviceClass, channel, runtime);
    }

    @Override
    public <T> T getNonReloading(Class<T> clazz, ServiceConfiguration serviceConf) {
        Channel channel = cache.getNonReloadingChannel(
                params, serviceConf, ChannelNames.nonReloading(clazz, params), OptionalInt.empty());

        return Reflection.callStaticFactoryMethod(clazz, channel, params.runtime());
    }

    @Override
    public <T> T getNonReloading(Class<T> clazz, ClientConfiguration clientConf) {
        String channelName = ChannelNames.nonReloading(clazz, params);
        Channel channel = getNonReloadingChannel(channelName, clientConf);
        return Reflection.callStaticFactoryMethod(clazz, channel, params.runtime());
    }

    @Override
    public Channel getChannel(String serviceName) {
        // TODO(dfox): reloading currently forgets which channel we were pinned to. Can we do this in a non-gross way?
        return new LiveReloadingChannel(
                getInternalDialogueChannel(serviceName), params.runtime().clients());
    }

    @Override
    public PerHostClientFactory perHost(String serviceName) {
        Preconditions.checkNotNull(serviceName, "serviceName");
        String channelName = ChannelNames.reloading(serviceName, params);

        Refreshable<List<DialogueChannel>> perHostDialogueChannels = params.scb()
                .map(block -> {
                    if (!block.services().containsKey(serviceName)) {
                        return ImmutableList.of();
                    }

                    ServiceConfiguration serviceConfiguration =
                            ServiceConfigurationFactory.of(block).get(serviceName);

                    ImmutableList.Builder<DialogueChannel> list = ImmutableList.builder();
                    for (int i = 0; i < serviceConfiguration.uris().size(); i++) {
                        ServiceConfiguration singleUriServiceConf = ServiceConfiguration.builder()
                                .from(serviceConfiguration)
                                .uris(ImmutableList.of(
                                        serviceConfiguration.uris().get(i)))
                                .build();

                        // subtle gotcha here is that every single one of these has the same channelName,
                        // which means metrics like the QueuedChannel counter will end up being the sum of all of them.
                        list.add(cache.getNonReloadingChannel(
                                params, singleUriServiceConf, channelName, OptionalInt.of(i)));
                    }
                    return list.build();
                });

        return new PerHostClientFactory() {
            @Override
            public Refreshable<List<Channel>> getPerHostChannels() {
                return perHostDialogueChannels.map(ImmutableList::copyOf);
            }

            @Override
            public <T> Refreshable<List<T>> getPerHost(Class<T> clientInterface) {
                return getPerHostChannels().map(channels -> channels.stream()
                        .map(chan -> Reflection.callStaticFactoryMethod(clientInterface, chan, params.runtime()))
                        .collect(ImmutableList.toImmutableList()));
            }

            @Override
            public String toString() {
                return "PerHostClientFactory{serviceName=" + serviceName + ", channels=" + perHostDialogueChannels.get()
                        + '}';
            }
        };
    }

    @Override
    public StickyChannelFactory getStickyChannels(String serviceName) {
        Refreshable<List<Channel>> perHostChannels = perHost(serviceName).getPerHostChannels();

        Refreshable<Supplier<Channel>> bestSupplier = perHostChannels.map(singleHostChannels -> {
            if (singleHostChannels.isEmpty()) {
                EmptyInternalDialogueChannel alwaysThrowing =
                        new EmptyInternalDialogueChannel(() -> new SafeIllegalStateException(
                                "Service not configured", SafeArg.of("serviceName", serviceName)));
                return () -> alwaysThrowing;
            }

            if (singleHostChannels.size() == 1) {
                return () -> singleHostChannels.get(0);
            }

            return StickyEndpointChannels.builder()
                    .channels(singleHostChannels.stream()
                            .map(c -> (DialogueChannel) c)
                            .collect(Collectors.toList()))
                    .channelName(ChannelNames.reloading(serviceName, params))
                    .taggedMetricRegistry(params.taggedMetrics())
                    .build();
        });

        return new StickyChannelFactory() {
            @Override
            public Channel getStickyChannel() {
                return bestSupplier.get().get();
            }

            @Override
            public <T> T getCurrentBest(Class<T> clientInterface) {
                return Reflection.callStaticFactoryMethod(clientInterface, getStickyChannel(), params.runtime());
            }

            @Override
            public String toString() {
                return "StickyChannelFactory{" + bestSupplier.get() + '}';
            }
        };
    }

    @Override
    public StickyChannelFactory2 getStickyChannels2(String serviceName) {
        Supplier<InternalDialogueChannel> internalDialogueChannel = getInternalDialogueChannel(serviceName);
        return new StickyChannelFactory2() {
            @Override
            public Channel getStickyChannel() {
                return internalDialogueChannel.get().stickyChannels().get();
            }

            @Override
            public <T> T sticky(Class<T> clientInterface) {
                return Reflection.callStaticFactoryMethod(clientInterface, getStickyChannel(), params.runtime());
            }
        };
    }

    @Override
    public DialogueClients.ReloadingFactory reloading(Refreshable<ServicesConfigBlock> scb) {
        return new ReloadingClientFactory(params.withScb(scb), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withUserAgent(UserAgent userAgent) {
        return new ReloadingClientFactory(params.withUserAgent(userAgent), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withMaxNumRetries(int value) {
        return new ReloadingClientFactory(params.withMaxNumRetries(value), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withRuntime(ConjureRuntime runtime) {
        return new ReloadingClientFactory(params.withRuntime(runtime), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withBlockingExecutor(ExecutorService executor) {
        return new ReloadingClientFactory(params.withBlockingExecutor(executor), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withTaggedMetrics(TaggedMetricRegistry metrics) {
        return new ReloadingClientFactory(params.withTaggedMetrics(metrics), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withNodeSelectionStrategy(NodeSelectionStrategy strategy) {
        return new ReloadingClientFactory(params.withNodeSelectionStrategy(strategy), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withFailedUrlCooldown(Duration _duration) {
        // Dialogue doesn't have a concept of 'failedUrlCooldown' so this is a no-op
        return this;
    }

    @Override
    public DialogueClients.ReloadingFactory withClientQoS(ClientConfiguration.ClientQoS value) {
        return new ReloadingClientFactory(params.withClientQoS(value), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withServerQoS(ClientConfiguration.ServerQoS value) {
        return new ReloadingClientFactory(params.withServerQoS(value), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withRetryOnTimeout(ClientConfiguration.RetryOnTimeout value) {
        return new ReloadingClientFactory(params.withRetryOnTimeout(value), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withSecurityProvider(Provider securityProvider) {
        return new ReloadingClientFactory(params.withSecurityProvider(securityProvider), cache);
    }

    @Override
    public DialogueClients.ReloadingFactory withHostEventsSink(HostEventsSink value) {
        return new ReloadingClientFactory(params.withHostEventsSink(value), cache);
    }

    @Override
    public String toString() {
        return "ReloadingClientFactory{params=" + params + ", cache=" + cache + '}';
    }

    /** Apply missing user-agent and/or hostEventSink based on {@code params}. */
    private ClientConfiguration hydrate(ClientConfiguration configuration) {
        if ((params.hostEventsSink().isPresent()
                        && configuration.hostEventsSink().isEmpty())
                || (params.userAgent().isPresent() && configuration.userAgent().isEmpty())) {
            return ClientConfiguration.builder()
                    .from(configuration)
                    .hostEventsSink(configuration.hostEventsSink().or(params::hostEventsSink))
                    .userAgent(configuration.userAgent().or(params::userAgent))
                    .build();
        }
        return configuration;
    }

    private Refreshable<InternalDialogueChannel> getInternalDialogueChannel(String serviceName) {
        Preconditions.checkNotNull(serviceName, "serviceName");
        String channelName = ChannelNames.reloading(serviceName, params);

        return params.scb().map(block -> {
            Preconditions.checkNotNull(block, "Refreshable must not provide a null ServicesConfigBlock");

            if (!block.services().containsKey(serviceName)) {
                return new EmptyInternalDialogueChannel(() -> new SafeIllegalStateException(
                        "Service not configured (config block not present)",
                        SafeArg.of("serviceName", serviceName),
                        SafeArg.of("available", block.services().keySet())));
            }

            if (block.services().get(serviceName).uris().isEmpty()) {
                return new EmptyInternalDialogueChannel(() -> {
                    Map<String, PartialServiceConfiguration> servicesWithUris =
                            Maps.filterValues(block.services(), c -> !c.uris().isEmpty());
                    return new SafeIllegalStateException(
                            "Service not configured (no URIs)",
                            SafeArg.of("serviceName", serviceName),
                            SafeArg.of("available", servicesWithUris.keySet()));
                });
            }

            ServiceConfiguration serviceConf =
                    ServiceConfigurationFactory.of(block).get(serviceName);

            DialogueChannel dialogueChannel =
                    cache.getNonReloadingChannel(params, serviceConf, channelName, OptionalInt.empty());
            return new InternalDialogueChannelFromDialogueChannel(dialogueChannel);
        });
    }

    private static final class EmptyInternalDialogueChannel implements InternalDialogueChannel {
        private final Supplier<? extends RuntimeException> exceptionSupplier;

        EmptyInternalDialogueChannel(Supplier<? extends RuntimeException> exceptionSupplier) {
            this.exceptionSupplier = exceptionSupplier;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
            return Futures.immediateFailedFuture(exceptionSupplier.get());
        }

        @Override
        public String toString() {
            return "EmptyInternalDialogueChannel{exceptionSupplier="
                    + exceptionSupplier.get().getMessage() + '}';
        }

        @Override
        public Supplier<Channel> stickyChannels() {
            return () -> this;
        }
    }

    /* Abstracts away DialogueChannel so that we can handle no-service/no-uri case in #getInternalDialogueChannel. */
    private interface InternalDialogueChannel extends Channel {
        Supplier<Channel> stickyChannels();
    }

    private static final class InternalDialogueChannelFromDialogueChannel implements InternalDialogueChannel {

        private final DialogueChannel dialogueChannel;

        private InternalDialogueChannelFromDialogueChannel(DialogueChannel dialogueChannel) {
            this.dialogueChannel = dialogueChannel;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            return dialogueChannel.execute(endpoint, request);
        }

        @Override
        public Supplier<Channel> stickyChannels() {
            return dialogueChannel.stickyChannels();
        }
    }

    @VisibleForTesting
    static final class LiveReloadingChannel implements Channel, EndpointChannelFactory {
        private final Refreshable<? extends Channel> refreshable;
        private final Clients utils;

        LiveReloadingChannel(Refreshable<? extends Channel> refreshable, Clients utils) {
            this.refreshable = refreshable;
            this.utils = utils;
        }

        /**
         * conjure-java generated code can use this to 'bind' to an endpoint upfront, and we live reload under the hood.
         */
        @Override
        public EndpointChannel endpoint(Endpoint endpoint) {
            Supplier<EndpointChannel> endpointChannel =
                    new LazilyMappedRefreshable<>(refreshable, channel -> utils.bind(channel, endpoint));
            return new SupplierEndpointChannel(endpointChannel);
        }

        /** Older codepath, not quite as performant. */
        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            Channel delegate = refreshable.get();
            return delegate.execute(endpoint, request);
        }

        @Override
        public String toString() {
            return "LiveReloadingChannel{" + refreshable.get() + '}';
        }
    }

    private static final class SupplierEndpointChannel implements EndpointChannel {
        private final Supplier<EndpointChannel> supplier;

        SupplierEndpointChannel(Supplier<EndpointChannel> supplier) {
            this.supplier = supplier;
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            return supplier.get().execute(request);
        }

        @Override
        public String toString() {
            return "SupplierEndpointChannel{" + supplier.get() + '}';
        }
    }

    /**
     * A lazy wrapper around {@link Refreshable#map(Function)} to reduce up-front client creation cost.
     * Binding is avoided entirely for endpoints that are never used.
     */
    private static final class LazilyMappedRefreshable<T, U> implements Supplier<U> {

        private final Supplier<Refreshable<U>> delegate;

        LazilyMappedRefreshable(Refreshable<T> refreshable, Function<? super T, U> function) {
            delegate = Suppliers.memoize(() -> refreshable.map(function));
        }

        @Override
        public U get() {
            Refreshable<U> mapped = delegate.get();
            return mapped.current();
        }

        @Override
        public String toString() {
            return "LazilyMappedRefreshable{" + delegate + '}';
        }
    }
}
