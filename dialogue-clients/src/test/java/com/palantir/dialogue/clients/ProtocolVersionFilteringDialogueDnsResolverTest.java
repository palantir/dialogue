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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSetMultimap;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.util.MapBasedDnsResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class ProtocolVersionFilteringDialogueDnsResolverTest {

    @Test
    void returns_only_ipv4_when_input_contains_only_ipv4() throws UnknownHostException {
        InetAddress address1 = InetAddress.getByAddress(new byte[] {1, 1, 1, 1});
        InetAddress address2 = InetAddress.getByAddress(new byte[] {2, 2, 2, 2});

        DialogueDnsResolver delegate =
                new MapBasedDnsResolver(ImmutableSetMultimap.of("foo.com", address1, "foo.com", address2));
        DialogueDnsResolver resolver = new ProtocolVersionFilteringDialogueDnsResolver(delegate);

        assertThat(resolver.resolve("foo.com")).containsExactly(address1, address2);
    }

    @Test
    void returns_only_ipv6_when_input_contains_only_ipv6() throws UnknownHostException {
        InetAddress address1 = InetAddress.getByName("0fa7:be4f:a4d2:4e33:22d0:c4b6:7ddb:19f0");
        InetAddress address2 = InetAddress.getByName("2b08:8eff:13b5:1246:5208:acb1:6b52:648a");

        DialogueDnsResolver delegate =
                new MapBasedDnsResolver(ImmutableSetMultimap.of("foo.com", address1, "foo.com", address2));
        DialogueDnsResolver resolver = new ProtocolVersionFilteringDialogueDnsResolver(delegate);

        assertThat(resolver.resolve("foo.com")).containsExactly(address1, address2);
    }

    @Test
    void returns_only_ipv4_when_input_contains_mix_of_ipv4_and_ipv6() throws UnknownHostException {
        InetAddress address1v4 = InetAddress.getByAddress(new byte[] {1, 1, 1, 1});
        InetAddress address2v4 = InetAddress.getByAddress(new byte[] {2, 2, 2, 2});
        InetAddress address1v6 = InetAddress.getByName("0fa7:be4f:a4d2:4e33:22d0:c4b6:7ddb:19f0");
        InetAddress address2v6 = InetAddress.getByName("2b08:8eff:13b5:1246:5208:acb1:6b52:648a");

        DialogueDnsResolver delegate = new MapBasedDnsResolver(ImmutableSetMultimap.of(
                "foo.com", address1v4, "foo.com", address1v6, "foo.com", address2v4, "foo.com", address2v6));
        DialogueDnsResolver resolver = new ProtocolVersionFilteringDialogueDnsResolver(delegate);

        assertThat(resolver.resolve("foo.com")).containsExactly(address1v4, address2v4);
    }
}
