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

import com.codahale.metrics.Meter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableSet;
import com.palantir.dialogue.clients.ClientDnsMetrics.Lookup_Result;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.InetAddress;
import java.time.Duration;

final class CachingFallbackDnsResolver implements DialogueDnsResolver {
    private static final SafeLogger log = SafeLoggerFactory.get(CachingFallbackDnsResolver.class);

    private final DialogueDnsResolver delegate;
    private final Meter lookupSuccess;
    private final Meter lookupFallback;
    private final Meter lookupFailure;

    private final Cache<String, ImmutableSet<InetAddress>> fallbackCache;

    CachingFallbackDnsResolver(DialogueDnsResolver delegate, TaggedMetricRegistry registry) {
        this.delegate = delegate;
        this.fallbackCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
        ClientDnsMetrics metrics = ClientDnsMetrics.of(registry);
        this.lookupSuccess = metrics.lookup(Lookup_Result.SUCCESS);
        this.lookupFallback = metrics.lookup(Lookup_Result.FALLBACK);
        this.lookupFailure = metrics.lookup(Lookup_Result.FAILURE);
    }

    @Override
    public ImmutableSet<InetAddress> resolve(String hostname) {
        ImmutableSet<InetAddress> result = delegate.resolve(hostname);
        if (result.isEmpty()) {
            ImmutableSet<InetAddress> maybeFallback = fallbackCache.getIfPresent(hostname);
            if (maybeFallback != null) {
                lookupFallback.mark();
                log.info(
                        "DNS resolution failed for host '{}', however fallback addresses are present in the cache",
                        UnsafeArg.of("hostname", hostname));
                return maybeFallback;
            } else {
                lookupFailure.mark();
            }
        } else {
            lookupSuccess.mark();
            fallbackCache.put(hostname, result);
        }
        return result;
    }
}
