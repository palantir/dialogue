/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.ProxyConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.core.DialogueExecutors;
import com.palantir.dialogue.core.TargetUri;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.Disposable;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.ref.Cleaner;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.annotation.Nullable;

final class DnsSupport {

    private static final SafeLogger log = SafeLoggerFactory.get(DnsSupport.class);
    private static final String SCHEDULER_NAME = "dialogue-client-dns-scheduler";

    private static final Cleaner cleaner = Cleaner.create(new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("dialogue-client-dns-%d")
            .build());

    /*
     * Shared single thread executor is reused between all DNS polling components. If it becomes oversaturated
     * we may wait slightly longer than expected before refreshing DNS info, but this is an
     * edge case where services are already operating in a degraded state.
     */
    @SuppressWarnings("deprecation") // Singleton registry for a singleton executor
    private static final Supplier<ScheduledExecutorService> sharedScheduler =
            Suppliers.memoize(() -> DialogueExecutors.newSharedSingleThreadScheduler(MetricRegistries.instrument(
                    SharedTaggedMetricRegistries.getSingleton(),
                    new ThreadFactoryBuilder()
                            .setNameFormat(SCHEDULER_NAME + "-%d")
                            .setDaemon(true)
                            .build(),
                    SCHEDULER_NAME)));

    /** Identical to the overload, but using the {@link #sharedScheduler}. */
    static <I> Refreshable<DnsResolutionResults<I>> pollForChanges(
            boolean dnsNodeDiscovery,
            DnsPollingSpec<I> spec,
            DialogueDnsResolver dnsResolver,
            Duration dnsRefreshInterval,
            TaggedMetricRegistry metrics,
            Refreshable<I> input) {
        return pollForChanges(
                dnsNodeDiscovery, spec, sharedScheduler.get(), dnsResolver, dnsRefreshInterval, metrics, input);
    }

    static <I> Refreshable<DnsResolutionResults<I>> pollForChanges(
            boolean dnsNodeDiscovery,
            DnsPollingSpec<I> spec,
            ScheduledExecutorService executor,
            DialogueDnsResolver dnsResolver,
            Duration dnsRefreshInterval,
            TaggedMetricRegistry metrics,
            Refreshable<I> input) {
        if (!dnsNodeDiscovery) {
            // When the feature flag is disabled, we only map from the input to a similar shape result.
            return input.map(value -> ImmutableDnsResolutionResults.of(value, Optional.empty()));
        }
        @SuppressWarnings("NullAway")
        SettableRefreshable<DnsResolutionResults<I>> dnsResolutionResult = Refreshable.create(null);

        Timer workerUpdateTimer = ClientDnsMetrics.of(metrics).refresh(spec.kind());
        DialogueDnsResolutionWorker<I> dnsResolutionWorker =
                new DialogueDnsResolutionWorker<>(spec, dnsResolver, dnsResolutionResult, workerUpdateTimer);

        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                dnsResolutionWorker,
                dnsRefreshInterval.toMillis(),
                dnsRefreshInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        Counter counter = ClientDnsMetrics.of(metrics).tasks(spec.kind());
        counter.inc();
        Disposable disposable = input.subscribe(dnsResolutionWorker::update);
        cleaner.register(dnsResolutionResult, new CleanupTask(disposable, future, counter));
        return dnsResolutionResult;
    }

    @Unsafe
    @Nullable
    static String tryGetHost(@Unsafe String uriString) {
        try {
            URI uri = new URI(uriString);
            return uri.getHost();
        } catch (URISyntaxException | RuntimeException e) {
            log.debug("Failed to parse URI", e);
            return null;
        }
    }

    /**
     * This prefix may reconfigure several aspects of the client to work better in a world where requests are routed
     * through a service mesh like istio/envoy.
     */
    private static final String MESH_PREFIX = "mesh-";

    static boolean isMeshMode(String uri) {
        return uri.startsWith(MESH_PREFIX);
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    static ImmutableList<TargetUri> getTargetUris(
            @Safe String serviceNameForLogging,
            Collection<String> uris,
            ProxySelector proxySelector,
            Optional<ImmutableSetMultimap<String, InetAddress>> resolvedHosts,
            TaggedMetricRegistry metrics) {
        List<TargetUri> targetUris = new ArrayList<>();
        boolean failedToParse = false;
        for (String uri : uris) {
            URI parsed = tryParseUri(metrics, serviceNameForLogging, uri);
            if (parsed == null || parsed.getHost() == null) {
                failedToParse = true;
                continue;
            }
            // When resolvedHosts is an empty optional, dns-based discovery is not supported.
            // Mesh mode does not require any form of dns updating because all dns results
            // are considered equivalent.
            // When a proxy is used, pre-resolved IP addresses have no impact. In many cases the
            // proxy handles DNS resolution.
            if (resolvedHosts.isEmpty() || DnsSupport.isMeshMode(uri) || usesProxy(proxySelector, parsed)) {
                targetUris.add(TargetUri.of(uri));
            } else {
                String host = parsed.getHost();
                Set<InetAddress> resolvedAddresses = resolvedHosts.get().get(host);
                if (resolvedAddresses.isEmpty()) {
                    log.info(
                            "Resolved no addresses for host '{}' of service '{}'",
                            UnsafeArg.of("host", host),
                            SafeArg.of("service", serviceNameForLogging));
                }
                for (InetAddress addr : resolvedAddresses) {
                    targetUris.add(
                            TargetUri.builder().uri(uri).resolvedAddress(addr).build());
                }
            }
        }
        if (targetUris.isEmpty() && failedToParse) {
            // Handle cases like "host:-1", but only when _all_ uris are invalid
            log.warn(
                    "Failed to parse all URIs, falling back to legacy DNS approach for service '{}'",
                    SafeArg.of("service", serviceNameForLogging));
            for (String uri : uris) {
                targetUris.add(TargetUri.of(uri));
            }
        }
        return ImmutableSet.copyOf(targetUris).asList();
    }

    static ProxySelector proxySelector(Optional<ProxyConfiguration> proxyConfiguration) {
        return proxyConfiguration.map(ClientConfigurations::createProxySelector).orElseGet(ProxySelector::getDefault);
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
    private static URI tryParseUri(TaggedMetricRegistry metrics, @Safe String serviceName, @Unsafe String uri) {
        try {
            URI result = new URI(uri);
            if (result.getHost() == null) {
                log.error(
                        "Failed to correctly parse URI {} for service {} due to null host component. "
                                + "This usually occurs due to invalid characters causing information to be "
                                + "parsed in the wrong uri component, often the host info lands in the authority.",
                        UnsafeArg.of("uri", uri),
                        SafeArg.of("service", serviceName));
                ClientUriMetrics.of(metrics).invalid(serviceName).mark();
            }
            return result;
        } catch (URISyntaxException | RuntimeException e) {
            log.error(
                    "Failed to parse URI {} for service {}",
                    UnsafeArg.of("uri", uri),
                    SafeArg.of("service", serviceName),
                    e);
            ClientUriMetrics.of(metrics).invalid(serviceName).mark();
            return null;
        }
    }

    private DnsSupport() {}

    // We define a concrete class here to avoid accidental lambda references to the
    // cleaner target.
    private static final class CleanupTask implements Runnable {

        private static final SafeLogger log = SafeLoggerFactory.get(CleanupTask.class);

        private final AtomicBoolean executed = new AtomicBoolean();
        private final Disposable disposable;
        private final ScheduledFuture<?> scheduledFuture;
        private final Counter activeTasks;

        private CleanupTask(Disposable disposable, ScheduledFuture<?> scheduledFuture, Counter activeTasks) {
            this.disposable = disposable;
            this.scheduledFuture = scheduledFuture;
            this.activeTasks = activeTasks;
        }

        @Override
        public void run() {
            if (!executed.getAndSet(true)) {
                log.debug("Unregistering dns update background worker");
                disposable.dispose();
                scheduledFuture.cancel(false);
                activeTasks.dec();
            }
        }
    }
}
