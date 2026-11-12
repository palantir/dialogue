/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
import java.util.Collections;
import java.util.Set;

/** Hostnames whose most recent uncached lookup reported that they do not exist. */
final class NonexistentHostnames {
    private final Set<String> hostnames = Collections.newSetFromMap(
            Caffeine.newBuilder().maximumSize(1000).<String, Boolean>build().asMap());

    void add(String hostname) {
        hostnames.add(hostname);
    }

    void remove(String hostname) {
        hostnames.remove(hostname);
    }

    boolean contains(String hostname) {
        return hostnames.contains(hostname);
    }
}
