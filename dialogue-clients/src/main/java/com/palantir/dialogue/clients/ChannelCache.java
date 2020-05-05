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
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.concurrent.ThreadSafe;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ThreadSafe
final class ChannelCache {
    private static final Logger log = LoggerFactory.getLogger(ChannelCache.class);

    // Ideally there should only be one ChannelCache per JVM, WeakSet helps us spot more instances.
    private static final Set<ChannelCache> liveInstances =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static final int MAX_CHANNELS = 1000;
    private final LoadingCache<ChannelCacheKey, Channel> channelCache = Caffeine.newBuilder()
            .maximumSize(MAX_CHANNELS) // arbitrary bound to avoid runaway OOM
            .build(this::createNonLiveReloadingChannel);

    // TODO(dfox): consider making this caffeine too?
    private final Map<String, ApacheCacheEntry> apacheCache = new ConcurrentHashMap<>();

    private ChannelCache() {}

    static ChannelCache createEmptyCache() {
        ChannelCache newCache = new ChannelCache();

        int numLiveInstances = liveInstances.size();
        if ((numLiveInstances > 0 && log.isInfoEnabled()) || log.isDebugEnabled()) {
            log.info(
                    "Created ChannelCache instance {}: {} {}",
                    SafeArg.of("numLiveInstances", numLiveInstances),
                    SafeArg.of("newCache", newCache),
                    SafeArg.of("existing", liveInstances),
                    new SafeRuntimeException("ChannelCache constructed here"));
        }

        liveInstances.add(newCache);
        return newCache;
    }

    Channel getNonReloadingChannel(
            ServiceConfiguration serviceConf,
            AugmentClientConfig augment,
            Optional<ScheduledExecutorService> retryExecutor,
            Optional<ExecutorService> blockingExecutor,
            @Safe String channelName) {
        if (log.isWarnEnabled() && channelCache.estimatedSize() >= MAX_CHANNELS * 0.75) {
            log.warn("channelCache nearing capacity - possible bug? {}", SafeArg.of("cache", this));
        }

        return channelCache.get(ImmutableChannelCacheKey.builder()
                .from(augment)
                .retryExecutor(retryExecutor)
                .serviceConf(serviceConf)
                .channelName(channelName)
                .blockingExecutor(blockingExecutor)
                .build());
    }

    private Channel createNonLiveReloadingChannel(ChannelCacheKey channelCacheRequest) {
        ApacheClientRequest request = ImmutableApacheClientRequest.builder()
                .from(channelCacheRequest)
                .channelName(channelCacheRequest.channelName())
                .serviceConf(stripUris(channelCacheRequest.serviceConf())) // we strip out uris to maximise cache hits
                .blockingExecutor(channelCacheRequest.blockingExecutor())
                .build();

        ApacheCacheEntry apacheClient = getApacheClient(request);

        DialogueChannel.Builder builder = DialogueChannel.builder()
                .channelName(channelCacheRequest.channelName())
                .clientConfiguration(ClientConfiguration.builder()
                        .from(apacheClient.conf())
                        .uris(channelCacheRequest.serviceConf().uris()) // restore uris
                        .build())
                .channelFactory(uri -> ApacheHttpClientChannels.createSingleUri(uri, apacheClient.client()));
        channelCacheRequest.retryExecutor().ifPresent(builder::retryScheduler);
        return builder.buildNonLiveReloading();
    }

    private ApacheCacheEntry getApacheClient(ApacheClientRequest request) {
        // lookup is based on channel name only, so we effectively have a per-service cache of size 1. Slightly
        // dangerous because we could flip back and forth if params are different, but keeps life simple.
        Optional<ApacheCacheEntry> cacheEntry = Optional.ofNullable(apacheCache.get(request.channelName()));

        Preconditions.checkState(ImmutableApacheClientRequest.copyOf(request).equals(request), "sanity check equality");
        if (cacheEntry.isPresent() && request.equals(cacheEntry.get().key())) { // real equality not reference equality!
            return cacheEntry.get();
        }

        ClientConfiguration clientConf = AugmentClientConfig.getClientConf(request.serviceConf(), request);

        ApacheHttpClientChannels.ClientBuilder clientBuilder = ApacheHttpClientChannels.clientBuilder()
                .clientConfiguration(clientConf)
                .clientName(request.channelName());
        request.blockingExecutor().ifPresent(clientBuilder::executor);
        ApacheHttpClientChannels.CloseableClient client = clientBuilder.build();

        ImmutableApacheCacheEntry newEntry = ImmutableApacheCacheEntry.builder()
                .key(request)
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
        return "ChannelCache@" + Integer.toHexString(System.identityHashCode(this)) + "{"
                + "apacheCache.size=" + apacheCache.size()
                + ", channelCache.size=" + channelCache.estimatedSize() + "/" + MAX_CHANNELS + '}';
    }

    @Value.Immutable
    interface ChannelCacheKey extends AugmentClientConfig {
        ServiceConfiguration serviceConf();

        String channelName();

        Optional<ScheduledExecutorService> retryExecutor();

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
        ApacheClientRequest key();

        ApacheHttpClientChannels.CloseableClient client();

        ClientConfiguration conf();
    }
}
