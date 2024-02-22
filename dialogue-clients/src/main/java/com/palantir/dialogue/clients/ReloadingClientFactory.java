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
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfigurationFactory;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
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
import com.palantir.logsafe.DoNotLog;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Provider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

final class ReloadingClientFactory implements DialogueClients.ReloadingFactory {
    private static final SafeLogger log = SafeLoggerFactory.get(ReloadingClientFactory.class);
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
                .clientName(channelName)
                .dnsResolver(params.dnsResolver());
        params.blockingExecutor().ifPresent(clientBuilder::executor);
        ApacheHttpClientChannels.CloseableClient apacheClient = clientBuilder.build();
        return new LiveReloadingChannel(
                DnsSupport.pollForChanges(
                                params.dnsResolver(), params.dnsRefreshInterval(), params.taggedMetrics(), clientConf)
                        .map(clientConfigWithTargets -> DialogueChannel.builder()
                                .channelName(channelName)
                                .clientConfiguration(clientConfigWithTargets.config())
                                .uris(clientConfigWithTargets.targets())
                                .factory(args -> ApacheHttpClientChannels.createSingleUri(args, apacheClient))
                                .build()),
                params.runtime().clients());
    }

    @Value.Immutable
    interface ReloadingParams extends AugmentClientConfig {

        Refreshable<ServicesConfigBlock> scb();

        /**
         * We use a lazy field here in order to avoid scheduling background work for each stage of
         * ReloadingClientFactory configuration, e.g. {@code factory.withUserAgent(agent).withTaggedMetrics(registry)}.
         */
        @Value.Lazy
        default Refreshable<ServicesConfigBlockWithResolvedHosts> resolvedConfig() {
            return DnsSupport.pollForChanges(dnsResolver(), dnsRefreshInterval(), taggedMetrics(), scb());
        }

        @Value.Default
        default ConjureRuntime runtime() {
            return DefaultConjureRuntime.builder().build();
        }

        @Value.Default
        default DialogueDnsResolver dnsResolver() {
            return new ProtocolVersionFilteringDialogueDnsResolver(DefaultDialogueDnsResolver.INSTANCE);
        }

        @Value.Default
        default Duration dnsRefreshInterval() {
            return Duration.ofSeconds(5);
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
        Channel channel = new LiveReloadingChannel(
                DnsSupport.pollForChanges(
                                params.dnsResolver(), params.dnsRefreshInterval(), params.taggedMetrics(), serviceConf)
                        .map(serviceConfWithTargets -> cache.getNonReloadingChannel(
                                params,
                                serviceConfWithTargets.config(),
                                serviceConfWithTargets.targets(),
                                ChannelNames.nonReloading(clazz, params),
                                OptionalInt.empty())),
                params.runtime().clients());
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

        Refreshable<List<DialogueChannel>> perHostDialogueChannels = configurationForService(serviceName)
                .map(block -> {
                    ServiceConfiguration serviceConfiguration =
                            block.serviceConfiguration().orElse(null);
                    if (serviceConfiguration == null) {
                        return ImmutableList.of();
                    }

                    ImmutableList<TargetUri> targetUris =
                            getTargetUrisForService(serviceName, serviceConfiguration, block.resolvedHosts());

                    if (targetUris.isEmpty()) {
                        return ImmutableList.of();
                    }

                    ImmutableList.Builder<DialogueChannel> list = ImmutableList.builder();
                    for (int i = 0; i < targetUris.size(); i++) {
                        TargetUri targetUri = targetUris.get(i);
                        ServiceConfiguration singleUriServiceConf = ServiceConfiguration.builder()
                                .from(serviceConfiguration)
                                .uris(ImmutableList.of(targetUri.uri()))
                                .build();

                        // subtle gotcha here is that every single one of these has the same channelName,
                        // which means metrics like the QueuedChannel counter will end up being the sum of all of them.
                        list.add(cache.getNonReloadingChannel(
                                params,
                                singleUriServiceConf,
                                ImmutableList.of(targetUri),
                                channelName,
                                OptionalInt.of(i)));
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
    public ReloadingFactory withDnsRefreshInterval(Duration value) {
        return new ReloadingClientFactory(params.withDnsRefreshInterval(value), cache);
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

    private Refreshable<InternalDialogueChannelConfiguration> configurationForService(String serviceName) {
        Preconditions.checkNotNull(serviceName, "serviceName");
        return params.resolvedConfig().map(block -> {
            Preconditions.checkNotNull(block, "Refreshable must not provide a null ServicesConfigBlock");
            if (!block.scb().services().containsKey(serviceName)) {
                return ImmutableInternalDialogueChannelConfiguration.of(Optional.empty(), ImmutableSetMultimap.of());
            }

            ServicesConfigBlock scb = block.scb();
            ServiceConfigurationFactory factory = ServiceConfigurationFactory.of(scb);
            try {
                // ServiceConfigurationFactory.get(serviceName) may throw when certain values are not present
                // in either the PartialServiceConfiguration or ServicesConfigBlock defaults.
                ServiceConfiguration serviceConf = factory.get(serviceName);
                ImmutableSet<String> hosts = extractHosts(serviceName, serviceConf);
                ImmutableSetMultimap<String, InetAddress> resolvedHostsForService =
                        ImmutableSetMultimap.copyOf(Multimaps.filterKeys(block.resolvedHosts(), hosts::contains));
                return ImmutableInternalDialogueChannelConfiguration.of(
                        Optional.of(serviceConf), resolvedHostsForService);
            } catch (RuntimeException e) {
                log.warn(
                        "Failed to produce a ServiceConfigurationFactory for service {}",
                        SafeArg.of("service", serviceName),
                        e);
                return ImmutableInternalDialogueChannelConfiguration.of(Optional.empty(), ImmutableSetMultimap.of());
            }
        });
    }

    private ImmutableList<TargetUri> getTargetUrisForService(
            String serviceName,
            ServiceConfiguration serviceConf,
            ImmutableSetMultimap<String, InetAddress> resolvedHosts) {
        ProxySelector proxySelector = serviceConf
                .proxy()
                .map(ClientConfigurations::createProxySelector)
                .orElseGet(ProxySelector::getDefault);
        // construct a TargetUri for each resolved address for this service's uris
        List<TargetUri> targetUris = new ArrayList<>();
        for (String uri : serviceConf.uris()) {
            URI parsed = tryParseUri(serviceName, uri);
            if (parsed == null || parsed.getHost() == null) {
                continue;
            }
            // Mesh mode does not require any form of dns updating because all dns results
            // are considered equivalent.
            // When a proxy is used, pre-resolved IP addresses have no impact. In many cases the
            // proxy handles DNS resolution.
            if (DnsSupport.isMeshMode(uri) || usesProxy(proxySelector, parsed)) {
                targetUris.add(TargetUri.of(uri));
            } else {
                String host = parsed.getHost();
                Set<InetAddress> resolvedAddresses = resolvedHosts.get(host);
                if (resolvedAddresses.isEmpty()) {
                    log.info(
                            "Resolved no addresses for host '{}' of service '{}'",
                            UnsafeArg.of("host", host),
                            SafeArg.of("service", serviceName));
                }
                for (InetAddress addr : resolvedAddresses) {
                    targetUris.add(
                            TargetUri.builder().uri(uri).resolvedAddress(addr).build());
                }
            }
        }
        return ImmutableList.copyOf(targetUris);
    }

    private Refreshable<InternalDialogueChannel> getInternalDialogueChannel(String serviceName) {
        Preconditions.checkNotNull(serviceName, "serviceName");
        String channelName = ChannelNames.reloading(serviceName, params);

        return configurationForService(serviceName).map(conf -> {
            Preconditions.checkNotNull(
                    conf, "Refreshable must not provide a null InternalDialogueChannelConfiguration");

            Optional<ServiceConfiguration> maybeServiceConf = conf.serviceConfiguration();

            if (maybeServiceConf.isEmpty()) {
                return new EmptyInternalDialogueChannel(() -> new SafeIllegalStateException(
                        "Service not configured (config block not present)", SafeArg.of("serviceName", serviceName)));
            }

            ServiceConfiguration serviceConf = maybeServiceConf.get();

            // Verify URIs are present (regardless of whether they can be DNS resolved)
            if (serviceConf.uris().isEmpty()) {
                return new EmptyInternalDialogueChannel(() -> new SafeIllegalStateException(
                        "Service not configured (no URIs)", SafeArg.of("serviceName", serviceName)));
            }

            List<TargetUri> targetUris = getTargetUrisForService(serviceName, serviceConf, conf.resolvedHosts());

            if (targetUris.isEmpty()) {
                return new EmptyInternalDialogueChannel(() -> new SafeIllegalStateException(
                        "Service not available (no addresses via DNS)", SafeArg.of("serviceName", serviceName)));
            }

            DialogueChannel dialogueChannel =
                    cache.getNonReloadingChannel(params, serviceConf, targetUris, channelName, OptionalInt.empty());
            return new InternalDialogueChannelFromDialogueChannel(dialogueChannel);
        });
    }

    private static boolean usesProxy(ProxySelector proxySelector, URI uri) {
        try {
            List<Proxy> proxies = proxySelector.select(uri);
            return !proxies.stream().allMatch(proxy -> Proxy.Type.DIRECT.equals(proxy.type()));
        } catch (RuntimeException e) {
            // Fall back to the simple path without scheduling recurring DNS resolution.
            return true;
        }
    }

    @Nullable
    private static URI tryParseUri(@Safe String serviceName, @Unsafe String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException | RuntimeException e) {
            log.error("Failed to parse URI for service {}", SafeArg.of("service", serviceName), e);
            return null;
        }
    }

    private static ImmutableSet<String> extractHosts(@Safe String serviceName, ServiceConfiguration configuration) {
        return configuration.uris().stream()
                .map(uri -> tryParseUri(serviceName, uri))
                .filter(Objects::nonNull)
                .map(URI::getHost)
                .filter(Objects::nonNull)
                .collect(ImmutableSet.toImmutableSet());
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

    @DoNotLog
    @Value.Immutable
    interface InternalDialogueChannelConfiguration {
        @Value.Parameter
        Optional<ServiceConfiguration> serviceConfiguration();

        @Value.Parameter
        ImmutableSetMultimap<String, InetAddress> resolvedHosts();
    }
}
