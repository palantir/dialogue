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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ThreadSafe
final class SharedResourcesImpl implements SharedResources {
    private static final Logger log = LoggerFactory.getLogger(SharedResourcesImpl.class);
    private final LoadingCache<String, StoreImpl> stores =
            Caffeine.newBuilder().maximumSize(100).build(namespace -> new StoreImpl());

    @Override
    public Store getStore(String namespace) {
        return stores.get(namespace);
    }

    @Override
    public void close() {
        Map<String, StoreImpl> map = stores.asMap();
        map.forEach((namespace, store) -> {
            try {
                log.info("Closing store for namespace {}", namespace);
                store.close();
            } catch (RuntimeException e) {
                log.info("Failed to close store, resources may be leaked", SafeArg.of("namespace", namespace), e);
            }
        });
    }

    @ThreadSafe
    private static final class StoreImpl implements Store, Closeable {
        private final ConcurrentHashMap<Object, Closeable> items = new ConcurrentHashMap<>();

        @Override
        public <K, V extends Closeable> V getOrComputeIfAbsent(
                K key, Function<K, V> defaultCreator, Class<V> requiredType) {
            Closeable object = items.compute(key, (k, existing) -> {
                return existing != null ? existing : defaultCreator.apply((K) k);
            });
            return requiredType.cast(object);
        }

        @Override
        public void close() {
            for (Closeable value : items.values()) {
                try {
                    value.close();
                } catch (IOException e) {
                    log.warn("Failed to close value", UnsafeArg.of("value", value), e);
                }
            }
        }
    }
}
