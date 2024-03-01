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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

class ProtocolVersionFilteringDialogueDnsResolver implements DialogueDnsResolver {
    private static final SafeLogger log = SafeLoggerFactory.get(ProtocolVersionFilteringDialogueDnsResolver.class);

    private final DialogueDnsResolver delegate;

    ProtocolVersionFilteringDialogueDnsResolver(DialogueDnsResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public ImmutableSet<InetAddress> resolve(String hostname) {
        return filter(delegate.resolve(hostname), hostname);
    }

    @Override
    public ImmutableSetMultimap<String, InetAddress> resolve(Iterable<String> hostnames) {
        ImmutableSetMultimap<String, InetAddress> resolved = delegate.resolve(hostnames);
        ImmutableSetMultimap.Builder<String, InetAddress> result = ImmutableSetMultimap.builder();
        for (String hostname : resolved.keySet()) {
            result.putAll(hostname, filter(resolved.get(hostname), hostname));
        }
        return result.build();
    }

    // TODO(dns): report metrics here
    private static ImmutableSet<InetAddress> filter(ImmutableSet<InetAddress> addresses, @Unsafe String hostname) {
        // Assume that if resolved addresses contain a mix of ipv4 and ipv6 addresses, then they
        // likely point to the same host, just using a different protocol. In that case, we discared the ipv6
        // addresses and prefer only ipv4.
        //
        // If the set of resolved addresses contains only ipv4 or only ipv6 addresses, then it is left unmodified.

        int numIpv4 = 0;
        int numIpv6 = 0;
        int numUnknown = 0;
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address) {
                ++numIpv4;
            } else if (address instanceof Inet6Address) {
                ++numIpv6;
            } else {
                ++numUnknown;
                log.warn(
                        "name resolution result contains address {} that is neither IPv4 nor IPv6 ({})",
                        UnsafeArg.of("address", address),
                        SafeArg.of("type", address.getClass()));
            }
        }

        if (numUnknown > 0) {
            return addresses;
        }

        if (numIpv4 > 0 && numIpv6 > 0) {
            ImmutableSet.Builder<InetAddress> onlyIpv4Addresses = ImmutableSet.builder();
            for (InetAddress address : addresses) {
                if (address instanceof Inet4Address) {
                    onlyIpv4Addresses.add(address);
                }
            }
            logFilteredAddresses(hostname, numIpv4, numIpv6);
            return onlyIpv4Addresses.build();
        }

        return addresses;
    }

    private static void logFilteredAddresses(@Unsafe String hostname, int numIpv4, int numIpv6) {
        // Localhost is logged at debug because this is most often expected.
        // We will likely drop non-localhost filtering to debug as well after this feature has rolled out.
        if ("localhost".equals(hostname)) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "using only resolved IPv4 addresses for host to avoid double-counting",
                        SafeArg.of("numIpv4", numIpv4),
                        SafeArg.of("numIpv6", numIpv6),
                        SafeArg.of("host", "localhost"));
            }
        } else {
            log.info(
                    "using only resolved IPv4 addresses for host to avoid double-counting",
                    SafeArg.of("numIpv4", numIpv4),
                    SafeArg.of("numIpv6", numIpv6),
                    UnsafeArg.of("host", hostname));
        }
    }
}
