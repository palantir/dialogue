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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class MapBasedDnsResolver implements DetailedDialogueDnsResolver {
    private final SetMultimap<String, InetAddress> map;
    private final Set<String> nonexistentHostnames = new HashSet<>();

    MapBasedDnsResolver(SetMultimap<String, InetAddress> map) {
        this.map = map;
    }

    void addNonexistentHostname(String hostname) {
        nonexistentHostnames.add(hostname);
    }

    void removeNonexistentHostname(String hostname) {
        nonexistentHostnames.remove(hostname);
    }

    @Override
    public Optional<ImmutableSet<InetAddress>> resolveDetailed(String hostname) {
        if (nonexistentHostnames.contains(hostname)) {
            return Optional.of(ImmutableSet.of());
        }
        if (!map.containsKey(hostname)) {
            return Optional.empty();
        }
        return Optional.of(ImmutableSet.copyOf(map.get(hostname)));
    }
}
