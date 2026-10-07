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
import static org.assertj.core.api.Assumptions.assumeThat;

import com.codahale.metrics.Meter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.MultimapBuilder.SetMultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.palantir.dialogue.clients.ClientDnsMetrics.Lookup_Result;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.util.MapBasedDnsResolver;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CachingFallbackDnsResolverTest {

    @Test
    void successfulUpdate() throws UnknownHostException {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        Meter lookupSuccessMeter = ClientDnsMetrics.of(registry).lookup(Lookup_Result.SUCCESS);
        SetMultimap<String, InetAddress> dnsEntries =
                SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        InetAddress initialAddress = InetAddress.getByAddress("host", new byte[] {127, 0, 0, 1});
        InetAddress updatedAddress = InetAddress.getByAddress("host", new byte[] {127, 0, 0, 2});
        dnsEntries.put("host", initialAddress);
        DialogueDnsResolver delegate = new MapBasedDnsResolver(dnsEntries);
        DialogueDnsResolver cached = new CachingFallbackDnsResolver(delegate, registry);
        assertThat(cached.resolve("host")).containsExactly(initialAddress);
        assertThat(lookupSuccessMeter.getCount()).isEqualTo(1);
        dnsEntries.clear();
        dnsEntries.put("host", updatedAddress);
        assertThat(cached.resolve("host")).containsExactly(updatedAddress);
        assertThat(lookupSuccessMeter.getCount()).isEqualTo(2);
    }

    @Test
    void failure() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        Meter lookupFailureMeter = ClientDnsMetrics.of(registry).lookup(Lookup_Result.FAILURE);
        DialogueDnsResolver delegate = new MapBasedDnsResolver(ImmutableSetMultimap.of());
        DialogueDnsResolver cached = new CachingFallbackDnsResolver(delegate, registry);
        assertThat(cached.resolve("host")).isEmpty();
        assertThat(lookupFailureMeter.getCount()).isEqualTo(1);
    }

    @Test
    void fallback() throws UnknownHostException {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        Meter lookupSuccessMeter = ClientDnsMetrics.of(registry).lookup(Lookup_Result.SUCCESS);
        Meter lookupFallbackMeter = ClientDnsMetrics.of(registry).lookup(Lookup_Result.FALLBACK);
        Meter lookupFailureMeter = ClientDnsMetrics.of(registry).lookup(Lookup_Result.FAILURE);
        SetMultimap<String, InetAddress> dnsEntries =
                SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        InetAddress address = InetAddress.getByAddress("host", new byte[] {127, 0, 0, 1});
        dnsEntries.put("host", address);
        DialogueDnsResolver delegate = new MapBasedDnsResolver(dnsEntries);
        DialogueDnsResolver cached = new CachingFallbackDnsResolver(delegate, registry);
        assertThat(cached.resolve("host")).containsExactly(address);
        assertThat(lookupSuccessMeter.getCount()).isEqualTo(1);
        assertThat(lookupFallbackMeter.getCount()).isEqualTo(0);
        dnsEntries.clear();
        assertThat(cached.resolve("host"))
                .as("host should still resolve to 'address' using the fallback cache")
                .containsExactly(address);
        assertThat(lookupSuccessMeter.getCount()).isEqualTo(1);
        assertThat(lookupFallbackMeter.getCount()).isEqualTo(1);
        assertThat(lookupFailureMeter.getCount()).isEqualTo(0);
    }

    @Test
    void noFallbackOnceHostDoesNotExist() throws UnknownHostException {
        assumeThat(System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("linux"))
                .describedAs("GAI Error Strings are only defined for Linux environments")
                .isTrue();

        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        ClientDnsMetrics metrics = ClientDnsMetrics.of(registry);
        String host = UUID.randomUUID() + ".palantir.com";
        InetAddress removedPodAddress = InetAddress.getByAddress(host, new byte[] {10, 0, 0, 1});
        AtomicBoolean podExists = new AtomicBoolean(true);
        // Like ReloadingClientFactory, each factory has its own resolver chain while the JVM negative cache is shared.
        DialogueDnsResolver firstSystem = new DefaultDialogueDnsResolver(registry);
        DialogueDnsResolver secondSystem = new DefaultDialogueDnsResolver(registry);
        DialogueDnsResolver first = new CachingFallbackDnsResolver(
                hostname -> podExists.get() ? ImmutableSet.of(removedPodAddress) : firstSystem.resolve(hostname),
                registry);
        DialogueDnsResolver second = new CachingFallbackDnsResolver(
                hostname -> podExists.get() ? ImmutableSet.of(removedPodAddress) : secondSystem.resolve(hostname),
                registry);
        assertThat(first.resolve(host)).containsExactly(removedPodAddress);
        assertThat(second.resolve(host)).containsExactly(removedPodAddress);

        podExists.set(false);
        assertThat(first.resolve(host)).as("EAI_NONAME").isEmpty();
        assertThat(second.resolve(host)).as("CACHED").isEmpty();
        assertThat(metrics.failure("EAI_NONAME").getCount()).isEqualTo(1);
        assertThat(metrics.failure("CACHED").getCount()).isEqualTo(1);
        assertThat(metrics.lookup(Lookup_Result.FALLBACK).getCount()).isEqualTo(0);
    }

    @Test
    void fallbackOnTransientFailure() throws UnknownHostException {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        String host = UUID.randomUUID() + ".palantir.com";
        InetAddress address = InetAddress.getByAddress(host, new byte[] {10, 0, 0, 1});
        AtomicBoolean dnsAvailable = new AtomicBoolean(true);
        DialogueDnsResolver delegate = hostname -> {
            if (dnsAvailable.get()) {
                return ImmutableSet.of(address);
            }
            DefaultDialogueDnsResolver.recordFailure(
                    hostname, new UnknownHostException(hostname + ": Temporary failure in name resolution"));
            return ImmutableSet.of();
        };
        DialogueDnsResolver cached = new CachingFallbackDnsResolver(delegate, registry);

        assertThat(cached.resolve(host)).containsExactly(address);
        dnsAvailable.set(false);
        assertThat(cached.resolve(host)).containsExactly(address);
        assertThat(ClientDnsMetrics.of(registry).lookup(Lookup_Result.FALLBACK).getCount())
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noFallbackFromCachedNegativeAcrossFactories(boolean includeHostname) throws UnknownHostException {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        String host = UUID.randomUUID() + ".palantir.com";
        InetAddress address = InetAddress.getByAddress(host, new byte[] {10, 0, 0, 1});
        SetMultimap<String, InetAddress> dnsEntries =
                SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        dnsEntries.put(host, address);
        DialogueDnsResolver first = new CachingFallbackDnsResolver(new MapBasedDnsResolver(dnsEntries), registry);
        DialogueDnsResolver second = new CachingFallbackDnsResolver(new MapBasedDnsResolver(dnsEntries), registry);
        assertThat(first.resolve(host)).containsExactly(address);
        assertThat(second.resolve(host)).containsExactly(address);

        dnsEntries.clear();
        DefaultDialogueDnsResolver.recordFailure(host, new UnknownHostException("Name or service not known"));
        assertThat(first.resolve(host)).isEmpty();
        DefaultDialogueDnsResolver.recordFailure(host, new UnknownHostException(includeHostname ? host : ""));
        assertThat(second.resolve(host)).isEmpty();
    }

    @Test
    void removedAddressStaysDiscardedDuringTransientFailure() throws UnknownHostException {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        String host = UUID.randomUUID() + ".palantir.com";
        InetAddress address = InetAddress.getByAddress(host, new byte[] {10, 0, 0, 1});
        SetMultimap<String, InetAddress> dnsEntries =
                SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        dnsEntries.put(host, address);
        DialogueDnsResolver cached = new CachingFallbackDnsResolver(new MapBasedDnsResolver(dnsEntries), registry);
        assertThat(cached.resolve(host)).containsExactly(address);

        dnsEntries.clear();
        DefaultDialogueDnsResolver.recordFailure(host, new UnknownHostException("Name or service not known"));
        assertThat(cached.resolve(host)).isEmpty();
        DefaultDialogueDnsResolver.recordFailure(host, new UnknownHostException(host));
        assertThat(cached.resolve(host)).isEmpty();
        DefaultDialogueDnsResolver.recordFailure(
                host, new UnknownHostException("Temporary failure in name resolution"));
        assertThat(cached.resolve(host)).isEmpty();
    }

    @Test
    void replacementAddressIsUsedForFallback() throws UnknownHostException {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        String host = "127.0.0.2";
        InetAddress oldAddress = InetAddress.getByAddress(host, new byte[] {10, 0, 0, 1});
        InetAddress replacementAddress = InetAddress.getByAddress(host, new byte[] {127, 0, 0, 2});
        SetMultimap<String, InetAddress> dnsEntries =
                SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        dnsEntries.put(host, oldAddress);
        DialogueDnsResolver cachedEntries = new MapBasedDnsResolver(dnsEntries);
        DialogueDnsResolver system = new DefaultDialogueDnsResolver(registry);
        AtomicBoolean replacementReady = new AtomicBoolean(false);
        DialogueDnsResolver cached = new CachingFallbackDnsResolver(
                hostname -> replacementReady.get() ? system.resolve(hostname) : cachedEntries.resolve(hostname),
                registry);
        assertThat(cached.resolve(host)).containsExactly(oldAddress);

        dnsEntries.clear();
        DefaultDialogueDnsResolver.recordFailure(host, new UnknownHostException("Name or service not known"));
        assertThat(cached.resolve(host)).isEmpty();
        replacementReady.set(true);
        assertThat(cached.resolve(host)).containsExactly(replacementAddress);

        replacementReady.set(false);
        DefaultDialogueDnsResolver.recordFailure(host, new UnknownHostException(host));
        assertThat(cached.resolve(host)).containsExactly(replacementAddress);
        DefaultDialogueDnsResolver.recordFailure(
                host, new UnknownHostException("Temporary failure in name resolution"));
        assertThat(cached.resolve(host)).containsExactly(replacementAddress);
    }
}
