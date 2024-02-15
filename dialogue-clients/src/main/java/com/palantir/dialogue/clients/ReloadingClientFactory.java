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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.clients.DialogueClients.StickyChannelFactory;
import com.palantir.dialogue.clients.DialogueClients.StickyChannelFactory2;
import com.palantir.dialogue.clients.DialogueClients.StickyChannelSession;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.core.StickyEndpointChannels;
import com.palantir.dialogue.core.TargetUri;
import com.palantir.dialogue.hc5.ApacheHttpClientChannels;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.InetAddress;
import java.net.URI;
import java.security.Provider;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.immutables.value.Value;

final class ReloadingClientFactory implements DialogueClients.ReloadingFactory {
    private final ImmutableReloadingParams params;
    private final ChannelCache cache;
    private final SettableRefreshable<ServicesConfigBlockWithResolvedHosts> dnsResolutionResult;

    ReloadingClientFactory(ImmutableReloadingParams params, ChannelCache cache) {
        this.params = params;
        this.cache = cache;
        this.dnsResolutionResult = Refreshable.create(ImmutableServicesConfigBlockWithResolvedHosts.of(
                ServicesConfigBlock.builder().build(), ImmutableSetMultimap.of()));

        DialogueDnsResolver dummyResolver = _hostname -> ImmutableSet.of(InetAddress.getLoopbackAddress());
        DialogueDnsResolutionWorker dnsResolutionWorker =
                new DialogueDnsResolutionWorker(dummyResolver, dnsResolutionResult);
        ExecutorService dnsResolutionExecutor = Executors.newSingleThreadExecutor();
        dnsResolutionExecutor.execute(dnsResolutionWorker);
        this.params.scb().subscribe(dnsResolutionWorker::update);
    }

    @Override
    public Channel getNonReloadingChannel(String channelName, ClientConfiguration input) {
        ClientConfiguration clientConf = hydrate(input);
        ApacheHttpClientChannels.ClientBuilder clientBuilder = ApacheHttpClientChannels.clientBuilder()
                .clientConfiguration(clientConf)
                .clientName(channelName)
                .dnsResolver(params.dnsResolver());
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

        @Value.Default
        default DialogueDnsResolver dnsResolver() {
            return DefaultDialogueDnsResolver.INSTANCE;
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
                return session().sticky(clientInterface);
            }

            @Override
            public StickyChannelSession session() {
                Supplier<Channel> channelSupplier = Suppliers.memoize(this::getStickyChannel);
                return new StickyChannelSession() {
                    @Override
                    public Channel getStickyChannel() {
                        return channelSupplier.get();
                    }

                    @Override
                    public <T> T sticky(Class<T> clientInterface) {
                        return Reflection.callStaticFactoryMethod(
                                clientInterface, getStickyChannel(), params.runtime());
                    }
                };
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
    public ReloadingFactory withDnsResolver(DialogueDnsResolver value) {
        return new ReloadingClientFactory(params.withDnsResolver(value), cache);
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

        return dnsResolutionResult
                .map(block -> {
                    Preconditions.checkNotNull(block, "Refreshable must not provide a null ServicesConfigBlock");

                    if (!block.scb().services().containsKey(serviceName)) {
                        return ImmutableInternalDialogueChannelConfiguration.of(
                                Optional.empty(), ImmutableSetMultimap.of());
                    }

                    Optional<ServiceConfiguration> serviceConf = Optional.of(
                            ServiceConfigurationFactory.of(block.scb()).get(serviceName));
                    ImmutableSetMultimap.Builder<String, InetAddress> resolvedHostsForService =
                            ImmutableSetMultimap.builder();
                    block.scb().services().get(serviceName).uris().stream()
                            .map(URI::create)
                            .map(URI::getHost)
                            .forEach(host -> resolvedHostsForService.putAll(
                                    host, block.resolvedHosts().get(host)));

                    return ImmutableInternalDialogueChannelConfiguration.of(
                            serviceConf, resolvedHostsForService.build());
                })
                .map(conf -> {
                    Preconditions.checkNotNull(
                            conf, "Refreshable must not provide a null InternalDialogueChannelConfiguration");

                    Optional<ServiceConfiguration> maybeServiceConf = conf.serviceConfiguration();

                    if (maybeServiceConf.isEmpty()) {
                        return new EmptyInternalDialogueChannel(() -> new SafeIllegalStateException(
                                "Service not configured (config block not present)",
                                SafeArg.of("serviceName", serviceName)));
                    }

                    // TODO(blaub): this is probably misleading... no resolvedHosts doesn't mean no URIs were set,
                    // just that we couldn't resolve the names; effectively those are the same scenarios though
                    if (conf.resolvedHosts().isEmpty()) {
                        return new EmptyInternalDialogueChannel(() -> new SafeIllegalStateException(
                                "Service not configured (no URIs)", SafeArg.of("serviceName", serviceName)));
                    }

                    // construct a TargetUri for each resolved address for this service's uris
                    ImmutableList.Builder<TargetUri> targetUris = ImmutableList.builder();
                    maybeServiceConf.get().uris().forEach(uri -> {
                        URI parsed = URI.create(uri);
                        Set<InetAddress> resolvedAddresses =
                                conf.resolvedHosts().get(parsed.getHost());
                        // as a special case, if there were no resolved addresses for this host, we want to set
                        // a TargetUri with an empty resolvedAddress
                        if (resolvedAddresses.isEmpty()) {
                            targetUris.add(TargetUri.builder().uri(uri).build());
                        } else {
                            resolvedAddresses.forEach(addr -> {
                                targetUris.add(TargetUri.builder()
                                        .uri(uri)
                                        .resolvedAddress(addr)
                                        .build());
                            });
                        }
                    });

                    DialogueChannel dialogueChannel = cache.getNonReloadingChannel(
                            params, maybeServiceConf.get(), targetUris.build(), channelName, OptionalInt.empty());
                    return new InternalDialogueChannelFromDialogueChannel(dialogueChannel);
                });
    }

    @Unsafe
    private static final class EmptyInternalDialogueChannel implements InternalDialogueChannel {
        private final Supplier<? extends RuntimeException> exceptionSupplier;

        EmptyInternalDialogueChannel(Supplier<? extends RuntimeException> exceptionSupplier) {
            this.exceptionSupplier = exceptionSupplier;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint _endpoint, Request _request) {
            return Futures.immediateFailedFuture(exceptionSupplier.get());
        }

        @Unsafe
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

    @Value.Immutable
    interface InternalDialogueChannelConfiguration {
        @Value.Parameter
        Optional<ServiceConfiguration> serviceConfiguration();

        @Value.Parameter
        ImmutableSetMultimap<String, InetAddress> resolvedHosts();
    }
}
