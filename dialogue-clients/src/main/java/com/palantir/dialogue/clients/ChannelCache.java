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
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.concurrent.ThreadSafe;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ThreadSafe
final class ChannelCache {
    private static final Logger log = LoggerFactory.getLogger(ChannelCache.class);

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

    private final LoadingCache<ChannelCacheKey, Channel> channelCache =
            Caffeine.newBuilder().maximumSize(MAX_CACHED_CHANNELS).build(this::createNonLiveReloadingChannel);
    private final int instanceNumber;

    private ChannelCache() {
        this.instanceNumber = INSTANCE_NUMBER.incrementAndGet(); // 1 indexed for human readability
    }

    static ChannelCache createEmptyCache() {
        ChannelCache newCache = new ChannelCache();
        LIVE_INSTANCES.add(newCache);

        int numLiveInstances = LIVE_INSTANCES.size();
        if ((numLiveInstances > 1 && log.isInfoEnabled()) || log.isDebugEnabled()) {
            log.info(
                    "Created ChannelCache instance #{} ({} alive): {} {}",
                    SafeArg.of("instanceNumber", newCache.instanceNumber),
                    SafeArg.of("totalAliveNow", numLiveInstances),
                    SafeArg.of("newCache", newCache),
                    SafeArg.of("existing", LIVE_INSTANCES),
                    new SafeRuntimeException("ChannelCache constructed here"));
        }

        return newCache;
    }

    Channel getNonReloadingChannel(
            ReloadingClientFactory.ReloadingParams reloadingParams,
            ServiceConfiguration serviceConf,
            @Safe String channelName) {
        if (log.isWarnEnabled() && channelCache.estimatedSize() >= MAX_CACHED_CHANNELS * 0.75) {
            log.warn("channelCache nearing capacity - possible bug? {}", SafeArg.of("cache", this));
        }

        return channelCache.get(ImmutableChannelCacheKey.builder()
                .from(reloadingParams)
                .blockingExecutor(reloadingParams.blockingExecutor())
                .serviceConf(serviceConf)
                .channelName(channelName)
                .build());
    }

    private Channel createNonLiveReloadingChannel(ChannelCacheKey channelCacheRequest) {
        ImmutableApacheClientRequest request = ImmutableApacheClientRequest.builder()
                .from(channelCacheRequest)
                .channelName(channelCacheRequest.channelName())
                .serviceConf(stripUris(channelCacheRequest.serviceConf())) // we strip out uris to maximise cache hits
                .blockingExecutor(channelCacheRequest.blockingExecutor())
                .build();

        ApacheCacheEntry apacheClient = getApacheClient(request);

        return DialogueChannel.builder()
                .channelName(channelCacheRequest.channelName())
                .clientConfiguration(ClientConfiguration.builder()
                        .from(apacheClient.conf())
                        .uris(channelCacheRequest.serviceConf().uris()) // restore uris
                        .build())
                .channelFactory(uri -> ApacheHttpClientChannels.createSingleUri(uri, apacheClient.client()))
                .buildNonLiveReloading();
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
                .clientName(request.channelName());
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

    @Override
    public String toString() {
        return "ChannelCache{"
                + "instanceNumber=" + instanceNumber
                + ", apacheCache.size=" + apacheCache.size()
                + ", channelCache.size=" + channelCache.estimatedSize() + "/" + MAX_CACHED_CHANNELS
                + '}';
    }

    @Value.Immutable
    interface ChannelCacheKey extends AugmentClientConfig {
        ServiceConfiguration serviceConf();

        String channelName();

        Optional<ExecutorService> blockingExecutor();
    }

    @Value.Immutable
    interface ApacheClientRequest extends AugmentClientConfig {
        ServiceConfiguration serviceConf();

        String channelName();

        Optional<ExecutorService> blockingExecutor();

        @Value.Check
        default void check() {
            Preconditions.checkState(serviceConf().uris().isEmpty(), "Uris must be empty");
        }
    }

    @Value.Immutable
    interface ApacheCacheEntry {
        ApacheClientRequest originalRequest();

        ApacheHttpClientChannels.CloseableClient client();

        ClientConfiguration conf();
    }
}
