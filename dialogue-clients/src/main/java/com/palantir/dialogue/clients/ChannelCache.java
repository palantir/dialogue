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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.core.TargetUri;
import com.palantir.dialogue.hc5.ApacheHttpClientChannels;
import com.palantir.logsafe.DoNotLog;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.Refreshable;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.annotation.concurrent.ThreadSafe;
import org.immutables.value.Value;

@ThreadSafe
final class ChannelCache {
    private static final SafeLogger log = SafeLoggerFactory.get(ChannelCache.class);

    /** Arbitrary bound to avoid runaway OOM. Creating more than this is still allowed, will just cause cache misses. */
    private static final int MAX_CACHED_CHANNELS = 1000;

    /** Ideally there should only be one ChannelCache per JVM, this AtomicInteger & WeakSet helps us spot extras. */
    private static final AtomicInteger INSTANCE_NUMBER = new AtomicInteger(0);

    private static final Set<ChannelCache> LIVE_INSTANCES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /**
     * apacheCache is indexed by channel name so we effectively have a per-service cache of size 1. Slightly
     * dangerous because we could flip back and forth if params are different, but allows us to close evicted clients
     * nicely.
     */
    private final Map<String, ApacheCacheEntry> apacheCache = new ConcurrentHashMap<>();

    private final LoadingCache<ChannelCacheKey, DialogueChannel> channelCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHED_CHANNELS)
            // Avoid holding onto old targets, which is now more common as we bind to resolved IP addresses
            .weakValues()
            .build(this::createNonLiveReloadingChannel);
    private final int instanceNumber;

    private ChannelCache() {
        this.instanceNumber = INSTANCE_NUMBER.incrementAndGet(); // 1 indexed for human readability
    }

    static ChannelCache createEmptyCache() {
        ChannelCache newCache = new ChannelCache();
        LIVE_INSTANCES.add(newCache);

        int numLiveInstances = LIVE_INSTANCES.size();
        if ((numLiveInstances > 5 && log.isInfoEnabled()) || log.isDebugEnabled()) {
            if (numLiveInstances >= 10) {
                log.info(
                        "Created ChannelCache instance #{} ({} alive): {}",
                        SafeArg.of("instanceNumber", newCache.instanceNumber),
                        SafeArg.of("totalAliveNow", numLiveInstances),
                        SafeArg.of("newCache", newCache),
                        new SafeRuntimeException("ChannelCache constructed here"));
            } else {
                log.info(
                        "Created ChannelCache instance #{} ({} alive): {}",
                        SafeArg.of("instanceNumber", newCache.instanceNumber),
                        SafeArg.of("totalAliveNow", numLiveInstances),
                        SafeArg.of("newCache", newCache));
            }
        }

        return newCache;
    }

    DialogueChannel getNonReloadingChannel(
            ReloadingClientFactory.ReloadingParams reloadingParams,
            ServiceConfiguration serviceConf,
            @Safe String channelName) {
        return getNonReloadingChannel(reloadingParams, serviceConf, channelName, Optional.empty());
    }

    DialogueChannel getNonReloadingChannel(
            ReloadingClientFactory.ReloadingParams reloadingParams,
            ServiceConfiguration serviceConf,
            @Safe String channelName,
            Optional<OverrideHostIndex> overrideHostIndex) {
        if (log.isWarnEnabled()) {
            long estimatedSize = channelCache.estimatedSize();
            if (estimatedSize >= MAX_CACHED_CHANNELS * 0.75) {
                log.warn(
                        "channelCache nearing capacity - possible bug? {} {} {}",
                        SafeArg.of("estimatedSize", estimatedSize),
                        SafeArg.of("maxSize", MAX_CACHED_CHANNELS),
                        SafeArg.of("cache", this));
            }
        }

        return channelCache.get(ImmutableChannelCacheKey.builder()
                .from(reloadingParams)
                .blockingExecutor(reloadingParams.blockingExecutor())
                .serviceConf(serviceConf)
                .channelName(channelName)
                .overrideHostIndex(overrideHostIndex)
                .dnsResolver(reloadingParams.dnsResolver())
                .dnsRefreshInterval(reloadingParams.dnsRefreshInterval())
                .dnsNodeDiscovery(overrideHostIndex.isEmpty() && reloadingParams.dnsNodeDiscovery())
                .build());
    }

    private DialogueChannel createNonLiveReloadingChannel(ChannelCacheKey channelCacheRequest) {
        ImmutableApacheClientRequest request = ImmutableApacheClientRequest.builder()
                .from(channelCacheRequest)
                .channelName(channelCacheRequest.channelName())
                .serviceConf(stripUris(channelCacheRequest.serviceConf())) // we strip out uris to maximise cache hits
                .blockingExecutor(channelCacheRequest.blockingExecutor())
                .dnsResolver(channelCacheRequest.dnsResolver())
                .build();

        ApacheCacheEntry apacheClient = getApacheClient(request);

        Refreshable<List<TargetUri>> targets;
        if (channelCacheRequest.overrideHostIndex().isPresent()) {
            targets = Refreshable.only(
                    List.of(channelCacheRequest.overrideHostIndex().get().target()));
        } else {
            DnsPollingSpec<ServiceConfiguration> spec = DnsPollingSpec.serviceConfig(channelCacheRequest.channelName());
            targets = DnsSupport.pollForChanges(
                            channelCacheRequest.dnsNodeDiscovery(),
                            spec,
                            channelCacheRequest.dnsResolver(),
                            channelCacheRequest.dnsRefreshInterval(),
                            channelCacheRequest.taggedMetrics(),
                            Refreshable.only(channelCacheRequest.serviceConf()))
                    .map(dnsResolutionResults -> DnsSupport.getTargetUris(
                            channelCacheRequest.channelName(),
                            channelCacheRequest.serviceConf().uris(),
                            DnsSupport.proxySelector(
                                    channelCacheRequest.serviceConf().proxy()),
                            dnsResolutionResults.resolvedHosts(),
                            channelCacheRequest.taggedMetrics()));
        }
        return DialogueChannel.builder()
                .channelName(channelCacheRequest.channelName())
                .clientConfiguration(ClientConfiguration.builder()
                        .from(apacheClient.conf())
                        .uris(channelCacheRequest.serviceConf().uris()) // restore uris
                        .build())
                .uris(targets)
                .factory(args -> ApacheHttpClientChannels.createSingleUri(args, apacheClient.client()))
                .overrideHostIndex(channelCacheRequest.overrideHostIndex().stream()
                        .mapToInt(OverrideHostIndex::index)
                        .findAny())
                .build();
    }

    @VisibleForTesting
    ApacheCacheEntry getApacheClient(ImmutableApacheClientRequest request) {
        Optional<ApacheCacheEntry> cacheEntry = Optional.ofNullable(apacheCache.get(request.channelName()));
        if (log.isDebugEnabled()) {
            log.debug(
                    "Lookup in apacheCache for {} (size {}) hit {}. apacheCacheKeys: {}",
                    SafeArg.of("channelName", request.channelName()),
                    SafeArg.of("size", apacheCache.size()),
                    SafeArg.of("hit", cacheEntry.isPresent()),
                    SafeArg.of("apacheCacheKeys", apacheCache.keySet()));
        }

        // real equality not reference equality!
        if (cacheEntry.isPresent() && request.equals(cacheEntry.get().originalRequest())) {
            return cacheEntry.get();
        } else {
            Preconditions.checkState(
                    ImmutableApacheClientRequest.copyOf(request).equals(request),
                    "A sane equals() method is required - this is a likely bug in Dialogue");
        }

        ClientConfiguration clientConf = AugmentClientConfig.getClientConf(request.serviceConf(), request);

        ApacheHttpClientChannels.ClientBuilder clientBuilder = ApacheHttpClientChannels.clientBuilder()
                .clientConfiguration(clientConf)
                .clientName(request.channelName())
                .dnsResolver(request.dnsResolver());
        request.blockingExecutor().ifPresent(clientBuilder::executor);
        ApacheHttpClientChannels.CloseableClient client = clientBuilder.build();

        ImmutableApacheCacheEntry newEntry = ImmutableApacheCacheEntry.builder()
                .originalRequest(request)
                .client(client)
                .conf(clientConf)
                .build();
        ApacheCacheEntry prev = apacheCache.put(request.channelName(), newEntry);

        try {
            if (prev != null) {
                prev.client().close(); // maybe this is unnecessary?
            }
        } catch (IOException e) {
            log.warn("Failed to close old apache client", e);
        }

        return newEntry;
    }

    private static ServiceConfiguration stripUris(ServiceConfiguration serviceConf) {
        return ServiceConfiguration.builder()
                .from(serviceConf)
                .uris(Collections.emptyList())
                .build();
    }

    @Safe
    @Override
    public String toString() {
        return "ChannelCache{"
                + "instanceNumber=" + instanceNumber
                + ", apacheCache.size=" + apacheCache.size()
                // Channel names are safe-loggable
                + ", apacheCache=" + apacheCache.keySet()
                + ", channelCache.size=" + channelCache.estimatedSize() + "/" + MAX_CACHED_CHANNELS
                + ", channelCache="
                // Channel names are safe-loggable
                + channelCache.asMap().keySet().stream()
                        .map(ChannelCacheKey::channelName)
                        .collect(Collectors.joining(", ", "[", "]"))
                + '}';
    }

    @DoNotLog
    @Value.Immutable
    interface ChannelCacheKey extends AugmentClientConfig {
        ServiceConfiguration serviceConf();

        Optional<ExecutorService> blockingExecutor();

        String channelName();

        Optional<OverrideHostIndex> overrideHostIndex();

        DialogueDnsResolver dnsResolver();

        Duration dnsRefreshInterval();

        boolean dnsNodeDiscovery();
    }

    @Unsafe
    @Value.Immutable
    interface OverrideHostIndex {
        @Value.Parameter(order = 0)
        int index();

        @Value.Parameter(order = 1)
        TargetUri target();

        static OverrideHostIndex of(int index, TargetUri target) {
            return ImmutableOverrideHostIndex.of(index, target);
        }
    }

    @DoNotLog
    @Value.Immutable
    interface ApacheClientRequest extends AugmentClientConfig {
        ServiceConfiguration serviceConf();

        String channelName();

        Optional<ExecutorService> blockingExecutor();

        DialogueDnsResolver dnsResolver();

        @Value.Check
        default void check() {
            Preconditions.checkState(serviceConf().uris().isEmpty(), "Uris must be empty");
        }
    }

    @DoNotLog
    @Value.Immutable
    interface ApacheCacheEntry {
        ApacheClientRequest originalRequest();

        ApacheHttpClientChannels.CloseableClient client();

        ClientConfiguration conf();
    }
}
