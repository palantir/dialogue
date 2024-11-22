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

package com.palantir.dialogue.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.dialogue.DialogueImmutablesStyle;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.caffeine.CacheStats;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import java.net.URI;
import java.time.Duration;
import javax.annotation.Nullable;
import org.immutables.value.Value;

public final class Uris {
    private static final SafeLogger log = SafeLoggerFactory.get(Uris.class);

    /**
     * This prefix may reconfigure several aspects of the client to work better in a world where requests are routed
     * through a service mesh like istio/envoy.
     */
    private static final String MESH_PREFIX = "mesh-";

    /**
     * Shared cache of string to parsed URI. This avoids excessive allocation overhead when parsing repeated targets.
     */
    private static final LoadingCache<String, MaybeUri> uriCache = CacheStats.of(
                    SharedTaggedMetricRegistries.getSingleton(), "dialogue-uri")
            .register(stats -> Caffeine.newBuilder()
                    .maximumWeight(100_000)
                    .<String, MaybeUri>weigher((key, _value) -> key.length())
                    .expireAfterAccess(Duration.ofMinutes(1))
                    .softValues()
                    .recordStats(stats)
                    .build(Uris::parse));

    @Unsafe
    public static MaybeUri tryParse(@Unsafe String uriString) {
        return uriCache.get(uriString);
    }

    @Unsafe
    private static MaybeUri parse(String uriString) {
        try {
            return MaybeUri.success(new URI(uriString));
        } catch (Exception e) {
            log.debug("Failed to parse URI", e);
            return MaybeUri.failure(
                    new SafeIllegalArgumentException("Failed to parse URI", e, UnsafeArg.of("uri", uriString)));
        }
    }

    public static void clearCache() {
        uriCache.invalidateAll();
    }

    /**
     * Returns true if the specified URI string is a mesh-mode formatted URI, configured to route through a
     * service mesh like istio/envoy.
     */
    public static boolean isMeshMode(String uri) {
        return uri.startsWith(MESH_PREFIX);
    }

    @Unsafe
    @Value.Immutable(builder = false)
    @DialogueImmutablesStyle
    public interface MaybeUri {
        @Value.Parameter
        @Nullable
        URI uri();

        @Value.Parameter
        @Nullable
        SafeIllegalArgumentException exception();

        @Value.Derived
        default boolean isSuccessful() {
            return uri() != null;
        }

        @Value.Derived
        default boolean isMeshMode() {
            URI uri = uri();
            return uri != null && Uris.isMeshMode(uri.getScheme());
        }

        @Unsafe
        @Value.Auxiliary
        default URI uriOrThrow() {
            SafeIllegalArgumentException exception = exception();
            if (exception != null) {
                throw exception;
            }
            return Preconditions.checkNotNull(uri(), "uri");
        }

        @Value.Check
        default void check() {
            Preconditions.checkState(uri() != null ^ exception() != null, "Only one of uri or exception can be null");
        }

        static @Unsafe MaybeUri success(URI uri) {
            return ImmutableMaybeUri.of(uri, null);
        }

        static @Unsafe MaybeUri failure(SafeIllegalArgumentException exception) {
            return ImmutableMaybeUri.of(null, exception);
        }
    }

    private Uris() {}
}
