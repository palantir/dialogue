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

package com.palantir.dialogue.util;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.palantir.dialogue.core.DialogueDnsResolver;
import java.net.InetAddress;

public class MapBasedDnsResolver implements DialogueDnsResolver {
    private final SetMultimap<String, InetAddress> map;

    public MapBasedDnsResolver(SetMultimap<String, InetAddress> map) {
        this.map = map;
    }

    @Override
    public ImmutableSet<InetAddress> resolve(String hostname) {
        return ImmutableSet.copyOf(map.get(hostname));
    }
}
