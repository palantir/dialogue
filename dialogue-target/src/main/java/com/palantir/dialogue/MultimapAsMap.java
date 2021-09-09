/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue;

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Map implementation wrapping a Multimap which uses the first value for any given key. */
final class MultimapAsMap<K, V> implements Map<K, V> {
    private final Multimap<K, V> multimap;

    MultimapAsMap(Multimap<K, V> multimap) {
        this.multimap = multimap;
    }

    @Override
    public int size() {
        return multimap.keySet().size();
    }

    @Override
    public boolean isEmpty() {
        return multimap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return multimap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        for (Entry<K, V> entry : entrySet()) {
            if (Objects.equals(entry.getValue(), value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        return Iterables.getFirst(multimap.get((K) key), null);
    }

    @Override
    public V put(K _key, V _value) {
        throw new UnsupportedOperationException("unmodifiable");
    }

    @Override
    public V remove(Object _key) {
        throw new UnsupportedOperationException("unmodifiable");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> _map) {
        throw new UnsupportedOperationException("unmodifiable");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("unmodifiable");
    }

    @Override
    public Set<K> keySet() {
        return multimap.keySet();
    }

    @Override
    @SuppressWarnings("SimplifyStreamApiCallChains") // Thanks intellij, but that would be recursive...
    public Collection<V> values() {
        return entrySet().stream().map(Entry::getValue).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return multimap.keySet().stream()
                .map(key -> new SimpleImmutableEntry<>(key, get(key)))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String toString() {
        return "MultimapAsMap{multimap=" + multimap + '}';
    }
}
