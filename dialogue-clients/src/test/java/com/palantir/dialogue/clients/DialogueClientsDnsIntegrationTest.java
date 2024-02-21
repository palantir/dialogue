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

import com.codahale.metrics.Counter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder.SetMultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.dialogue.util.MapBasedDnsResolver;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class DialogueClientsDnsIntegrationTest {

    // This test does not use TLS by design because it would need custom certs to pass hostname verification.
    // We only want to verify the configured DNS resolver is used.
    @Test
    void custom_dns_resolver() {
        String randomHostname = UUID.randomUUID().toString();

        List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
        Undertow undertow = Undertow.builder()
                .addHttpListener(0, "localhost", new BlockingHandler(exchange -> {
                    requestPaths.add(exchange.getRequestPath());
                    exchange.setStatusCode(200);
                }))
                .build();
        undertow.start();
        try {
            ReloadingFactory factory = DialogueClients.create(Refreshable.only(ServicesConfigBlock.builder()
                            .defaultSecurity(TestConfigurations.SSL_CONFIG)
                            .putServices(
                                    "foo",
                                    PartialServiceConfiguration.builder()
                                            .addUris(getUri(undertow, randomHostname))
                                            .build())
                            .build()))
                    .withUserAgent(TestConfigurations.AGENT)
                    .withDnsResolver(hostname -> {
                        if (randomHostname.equals(hostname)) {
                            try {
                                return ImmutableSet.of(
                                        InetAddress.getByAddress(randomHostname, new byte[] {127, 0, 0, 1}));
                            } catch (UnknownHostException ignored) {
                                // fall-through
                            }
                        }
                        return ImmutableSet.of();
                    });

            SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "foo");
            client.voidToVoid();
            assertThat(requestPaths).containsExactly("/voidToVoid");
        } finally {
            undertow.stop();
        }
    }

    @Test
    void dns_refresh_works() throws UnknownHostException {
        Duration dnsRefreshInterval = Duration.ofMillis(50);
        String hostOne = "hostone";
        String hostTwo = "hosttwo";

        SetMultimap<String, InetAddress> dnsEntries =
                SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        DialogueDnsResolver dnsResolver = new MapBasedDnsResolver(dnsEntries);

        dnsEntries.put(hostOne, InetAddress.getByAddress(hostOne, new byte[] {127, 0, 0, 1}));

        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        Counter activeTasks = ClientDnsMetrics.of(metrics).tasks();

        List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
        Undertow undertow = Undertow.builder()
                .addHttpListener(0, "localhost", new BlockingHandler(exchange -> {
                    requestPaths.add(exchange.getRequestPath());
                    exchange.setStatusCode(200);
                }))
                .build();
        undertow.start();
        try {
            DialogueClients.ReloadingFactory factory = DialogueClients.create(
                            Refreshable.only(ServicesConfigBlock.builder()
                                    .defaultSecurity(TestConfigurations.SSL_CONFIG)
                                    .putServices(
                                            "foo",
                                            PartialServiceConfiguration.builder()
                                                    .addUris(getUri(undertow, hostOne) + "/one")
                                                    .addUris(getUri(undertow, hostTwo) + "/two")
                                                    .build())
                                    .build()))
                    .withDnsResolver(dnsResolver)
                    .withDnsRefreshInterval(dnsRefreshInterval)
                    .withUserAgent(TestConfigurations.AGENT)
                    .withTaggedMetrics(metrics);

            SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "foo");
            client.voidToVoid();

            assertThat(activeTasks.getCount()).isEqualTo(1);

            // Ensure the dns update thread sticks around after a GC
            System.gc();

            dnsEntries.put(hostTwo, InetAddress.getByAddress(hostOne, new byte[] {127, 0, 0, 1}));
            dnsEntries.removeAll(hostOne);

            // Ensure the subsequent code has a chance to push updates after waiting for
            // a dns refresh task to begin.
            Uninterruptibles.sleepUninterruptibly(dnsRefreshInterval.plus(Duration.ofMillis(100)));

            client.voidToVoid();
            assertThat(requestPaths).containsExactly("/one/voidToVoid", "/two/voidToVoid");

            // Ensure the dns update task sticks around after a GC
            System.gc();
            assertThat(activeTasks.getCount())
                    .as("Background refresh task should still be polling")
                    .isEqualTo(1);
        } finally {
            undertow.stop();
        }
    }

    @SuppressWarnings("ReassignedVariable")
    @Test
    void dns_refresh_works_without_factory_ref() throws UnknownHostException {
        Duration dnsRefreshInterval = Duration.ofMillis(50);
        String hostOne = "hostone";
        String hostTwo = "hosttwo";

        SetMultimap<String, InetAddress> dnsEntries =
                SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        DialogueDnsResolver dnsResolver = new MapBasedDnsResolver(dnsEntries);

        dnsEntries.put(hostOne, InetAddress.getByAddress(hostOne, new byte[] {127, 0, 0, 1}));

        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        Counter activeTasks = ClientDnsMetrics.of(metrics).tasks();

        List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
        Undertow undertow = Undertow.builder()
                .addHttpListener(0, "localhost", new BlockingHandler(exchange -> {
                    requestPaths.add(exchange.getRequestPath());
                    exchange.setStatusCode(200);
                }))
                .build();
        undertow.start();
        try {

            // reassigned to null later so that the target may be garbage collected
            @SuppressWarnings("unused")
            DialogueClients.ReloadingFactory factory = DialogueClients.create(
                            Refreshable.only(ServicesConfigBlock.builder()
                                    .defaultSecurity(TestConfigurations.SSL_CONFIG)
                                    .putServices(
                                            "foo",
                                            PartialServiceConfiguration.builder()
                                                    .addUris(getUri(undertow, hostOne) + "/one")
                                                    .addUris(getUri(undertow, hostTwo) + "/two")
                                                    .build())
                                    .build()))
                    .withDnsResolver(dnsResolver)
                    .withDnsRefreshInterval(dnsRefreshInterval)
                    .withUserAgent(TestConfigurations.AGENT)
                    .withTaggedMetrics(metrics);

            WeakReference<DialogueClients.ReloadingFactory> factoryRef = new WeakReference<>(factory);
            // reassigned to null later so that the target may be garbage collected
            @SuppressWarnings("unused")
            SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "foo");
            factory = null;

            // The factory should not be released at this point
            System.gc();
            assertThat(factoryRef.get()).isNotNull();

            client.voidToVoid();

            assertThat(activeTasks.getCount()).isEqualTo(1);

            dnsEntries.put(hostTwo, InetAddress.getByAddress(hostOne, new byte[] {127, 0, 0, 1}));
            dnsEntries.removeAll(hostOne);

            // Ensure the subsequent code has a chance to push updates after waiting for
            // a dns refresh task to begin.
            Uninterruptibles.sleepUninterruptibly(dnsRefreshInterval.plus(Duration.ofMillis(100)));

            client.voidToVoid();
            assertThat(requestPaths).containsExactly("/one/voidToVoid", "/two/voidToVoid");

            assertThat(factoryRef.get())
                    .as("The factory should not be possible to collect while clients are referenced")
                    .isNotNull();

            // Dropping the client reference should allow the factory to be freed
            client = null;

            Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                // Give the GC a nudge so the cleaner is likely to fire.
                System.gc();
                assertThat(factoryRef.get())
                        .as("Expected the factory reference to be freed")
                        .isNull();
            });

            Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                // Force a gc to help the cleaner along
                System.gc();
                assertThat(activeTasks.getCount())
                        .as("The background task should no longer exist")
                        .isZero();
            });
        } finally {
            undertow.stop();
        }
    }

    private static String getUri(Undertow undertow, String hostname) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format(
                "%s://%s:%d",
                listenerInfo.getProtcol(), hostname, ((InetSocketAddress) listenerInfo.getAddress()).getPort());
    }
}
