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
import com.google.common.collect.ImmutableSetMultimap;
import com.palantir.dialogue.core.DialogueDnsResolver;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

class ProtocolVersionFilteringDialogueDnsResolver implements DialogueDnsResolver {
    private final DialogueDnsResolver delegate;

    ProtocolVersionFilteringDialogueDnsResolver(DialogueDnsResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public ImmutableSet<InetAddress> resolve(String hostname) {
        return filter(delegate.resolve(hostname));
    }

    @Override
    public ImmutableSetMultimap<String, InetAddress> resolve(Iterable<String> hostnames) {
        ImmutableSetMultimap<String, InetAddress> resolved = delegate.resolve(hostnames);
        ImmutableSetMultimap.Builder<String, InetAddress> result = ImmutableSetMultimap.builder();
        resolved.asMap().forEach((host, addresses) -> result.putAll(host, filter(addresses)));
        return result.build();
    }

    // TODO(dns): report metrics here
    private static ImmutableSet<InetAddress> filter(Collection<InetAddress> addresses) {
        // Assume that if resolved addresses contain a mix of ipv4 and ipv6 addresses, then they
        // likely point to the same host, just using a different protocol. In that case, we discared the ipv6
        // addresses and prefer only ipv4.
        //
        // If the set of resolved addresses contains only ipv4 or only ipv6 addresses, then it is left unmodified.

        Set<InetAddress> ipv4Addresses =
                addresses.stream().filter(addr -> addr instanceof Inet4Address).collect(Collectors.toUnmodifiableSet());
        Set<InetAddress> ipv6Addresses =
                addresses.stream().filter(addr -> addr instanceof Inet6Address).collect(Collectors.toUnmodifiableSet());

        if (ipv4Addresses.isEmpty() && ipv6Addresses.isEmpty()) {
            return ImmutableSet.of();
        } else if (ipv6Addresses.isEmpty()) {
            return ImmutableSet.copyOf(ipv4Addresses);
        } else if (ipv4Addresses.isEmpty()) {
            return ImmutableSet.copyOf(ipv6Addresses);
        } else {
            // only include ipv4 addresses
            return ImmutableSet.copyOf(ipv4Addresses);
        }
    }
}
