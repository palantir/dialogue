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

package com.palantir.dialogue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TokenizedMultiMap implements ListMultimap<String, String> {

    private final Map<String, String> internal;

    TokenizedMultiMap(Map<String, String> internal) {
        this.internal = internal;
    }

    @Override
    public List<String> get(String key) {
        String multi = internal.get(key);
        if (multi == null) {
            return null;
        }
        return MultiString.decode(multi);
    }

    @Override
    public Set<String> keySet() {
        return internal.keySet();
    }

    @Override
    public Multiset<String> keys() {
        ImmutableMultiset.Builder<String> keys = ImmutableMultiset.builder();
        internal.forEach((key, value) -> {
            keys.addCopies(key, MultiString.decodeCount(value));
        });
        return keys.build();
    }

    @Override
    public Collection<String> values() {
        if (internal.isEmpty()) {
            return Collections.emptyList();
        }

        ImmutableList.Builder<String> builder = ImmutableList.builderWithExpectedSize(internal.size());
        internal.values().forEach(multi -> {
            builder.addAll(MultiString.decode(multi));
        });
        return builder.build();
    }

    @Override
    public Collection<Map.Entry<String, String>> entries() {
        if (internal.isEmpty()) {
            return Collections.emptyList();
        }

        ImmutableList.Builder<Map.Entry<String, String>> builder =
                ImmutableList.builderWithExpectedSize(internal.size());
        internal.forEach((key, multi) -> {
            List<String> decoded = MultiString.decode(multi);
            for (int i = 0; i < decoded.size(); i++) {
                builder.add(Maps.immutableEntry(key, decoded.get(i)));
            }
        });
        return builder.build();
    }

    @Override
    public List<String> removeAll(Object key) {
        throw new UnsupportedOperationException("No");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("No");
    }

    @Override
    public int size() {
        if (internal.isEmpty()) {
            return 0;
        }

        int size = 0;
        Collection<String> values = internal.values();
        for (String value : values) {
            size += MultiString.decodeCount(value);
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        return internal.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return internal.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public boolean containsEntry(Object key, Object value) {
        return entries().contains(Maps.immutableEntry(key, value));
    }

    @Override
    public boolean put(String key, String value) {
        throw new UnsupportedOperationException("No");
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException("No");
    }

    @Override
    public boolean putAll(String key, Iterable<? extends String> values) {
        throw new UnsupportedOperationException("No");
    }

    @Override
    public boolean putAll(Multimap<? extends String, ? extends String> multimap) {
        throw new UnsupportedOperationException("No");
    }

    @Override
    public List<String> replaceValues(String key, Iterable<? extends String> values) {
        throw new UnsupportedOperationException("No");
    }

    @Override
    public Map<String, Collection<String>> asMap() {
        if (internal.isEmpty()) {
            return ImmutableMap.of();
        }

        ImmutableMap.Builder<String, Collection<String>> builder = ImmutableMap.builderWithExpectedSize(internal.size());
        internal.forEach((key, value) -> {
            builder.put(key, MultiString.decode(value));
        });
        return builder.build();
    }

    @Override
    public String toString() {
        return "TokenizedMultiMap{" + "internal=" + internal + '}';
    }
}
