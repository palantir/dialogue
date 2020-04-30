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

import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import com.palantir.logsafe.Preconditions;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.immutables.value.Value;

final class ApacheCache {
    private final Map<String, CacheEntry> map = new ConcurrentHashMap<>();

    public CacheEntry get(GetApacheClient request) {
        // lookup is based on service name only, so we effectively have a per-service cache of size 1. Slightly
        // dangerous because we could flip back and forth if params are different, but keeps life simple.
        Optional<CacheEntry> cacheEntry = Optional.ofNullable(map.get(request.serviceName()));

        Preconditions.checkState(ImmutableGetApacheClient.copyOf(request).equals(request), "sanity check equality");
        if (cacheEntry.isPresent() && request.equals(cacheEntry.get().key())) { // real equality not reference equality!
            return cacheEntry.get();
        }

        ClientConfiguration clientConf = Facade.mix(request.serviceConf(), request.params());
        ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(clientConf, request.serviceName());

        ImmutableCacheEntry newEntry = ImmutableCacheEntry.builder()
                .key(request)
                .client(client)
                .conf(clientConf)
                .build();
        CacheEntry prev = map.put(request.serviceName(), newEntry);

        try {
            if (prev != null) {
                prev.client().close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return newEntry;
    }

    @Value.Immutable
    interface CacheEntry {
        GetApacheClient key();

        ApacheHttpClientChannels.CloseableClient client();

        ClientConfiguration conf();
    }

    @Value.Immutable
    interface GetApacheClient {
        String serviceName();

        ServiceConfiguration serviceConf();

        Facade.AugmentClientConfig params();

        @Value.Check
        default void check() {
            Preconditions.checkState(serviceConf().uris().isEmpty(), "Uris must be empty");
        }
    }
}
