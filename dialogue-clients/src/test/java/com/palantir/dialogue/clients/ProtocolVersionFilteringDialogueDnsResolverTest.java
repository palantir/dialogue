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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.palantir.dialogue.core.DialogueDnsResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class ProtocolVersionFilteringDialogueDnsResolverTest {

    @Test
    void returns_only_ipv4_when_input_contains_only_ipv4() throws UnknownHostException {
        ImmutableSet<InetAddress> resolvedAddresses = ImmutableSet.of(
                InetAddress.getByAddress(new byte[] {1, 1, 1, 1}), InetAddress.getByAddress(new byte[] {2, 2, 2, 2}));

        DialogueDnsResolver delegate = mock(DialogueDnsResolver.class);
        when(delegate.resolve(anyString())).thenReturn(resolvedAddresses);

        DialogueDnsResolver resolver = new ProtocolVersionFilteringDialogueDnsResolver(delegate);

        assertThat(resolver.resolve("foo")).isEqualTo(resolvedAddresses);
    }

    @Test
    void returns_only_ipv6_when_input_contains_only_ipv6() throws UnknownHostException {
        ImmutableSet<InetAddress> resolvedAddresses = ImmutableSet.of(
                InetAddress.getByName("0fa7:be4f:a4d2:4e33:22d0:c4b6:7ddb:19f0"),
                InetAddress.getByName("2b08:8eff:13b5:1246:5208:acb1:6b52:648a"));

        DialogueDnsResolver delegate = mock(DialogueDnsResolver.class);
        when(delegate.resolve(anyString())).thenReturn(resolvedAddresses);

        DialogueDnsResolver resolver = new ProtocolVersionFilteringDialogueDnsResolver(delegate);

        assertThat(resolver.resolve("foo")).isEqualTo(resolvedAddresses);
    }

    @Test
    void returns_only_ipv4_when_input_contains_mix_of_ipv4_and_ipv6() throws UnknownHostException {
        ImmutableSet<InetAddress> resolvedIpv4Addresses = ImmutableSet.of(
                InetAddress.getByAddress(new byte[] {1, 1, 1, 1}), InetAddress.getByAddress(new byte[] {2, 2, 2, 2}));
        ImmutableSet<InetAddress> resolvedIpv6Addresses = ImmutableSet.of(
                InetAddress.getByName("0fa7:be4f:a4d2:4e33:22d0:c4b6:7ddb:19f0"),
                InetAddress.getByName("2b08:8eff:13b5:1246:5208:acb1:6b52:648a"));
        ImmutableSet<InetAddress> resolvedAddresses = ImmutableSet.<InetAddress>builder()
                .addAll(resolvedIpv4Addresses)
                .addAll(resolvedIpv6Addresses)
                .build();

        DialogueDnsResolver delegate = mock(DialogueDnsResolver.class);
        when(delegate.resolve(anyString())).thenReturn(resolvedAddresses);

        DialogueDnsResolver resolver = new ProtocolVersionFilteringDialogueDnsResolver(delegate);

        assertThat(resolver.resolve("foo")).isEqualTo(resolvedIpv4Addresses);
    }
}
