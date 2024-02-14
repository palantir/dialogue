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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import java.net.InetAddress;

public interface DialogueDnsResolver {

    /**
     * Resolve {@link InetAddress addresses} of the provided {@code hostname}. This method does not throw,
     * instead returning an empty set when resolution fails.
     */
    ImmutableSet<InetAddress> resolve(String hostname);

    /**
     * Resolve hostnames in bulk.
     */
    default ImmutableSetMultimap<String, InetAddress> resolve(Iterable<String> hostnames) {
        ImmutableSet<String> uniqueHostnames = ImmutableSet.copyOf(hostnames);
        ImmutableSetMultimap.Builder<String, InetAddress> builder = ImmutableSetMultimap.builder();
        for (String hostname : uniqueHostnames) {
            builder.putAll(hostname, resolve(hostname));
        }
        return builder.build();
    }
}
