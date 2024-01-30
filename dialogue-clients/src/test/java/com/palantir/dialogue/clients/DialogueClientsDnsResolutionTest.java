/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DialogueClientsDnsResolutionTest {
    private static final String FOO_PATH = "/foo1";
    private Undertow undertow;
    private HttpHandler undertowHandler;

    @BeforeEach
    public void before() {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        undertow = Undertow.builder()
                .addHttpsListener(
                        0,
                        "localhost",
                        sslContext,
                        new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();
    }

    @AfterEach
    public void after() {
        if (undertow != null) {
            undertow.stop();
        }
    }

    @Test
    void reloadingFactoryDnsTaskCleanup() {
        List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(200);
        };

        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        Refreshable<ServicesConfigBlock> refreshable = Refreshable.only(ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        "foo",
                        PartialServiceConfiguration.builder()
                                .addUris(getUri(undertow) + FOO_PATH)
                                .build())
                .build());
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(refreshable).withUserAgent(TestConfigurations.AGENT);

        @SuppressWarnings("unused")
        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "foo");
        WeakReference<SampleServiceBlocking> clientWeakRef = new WeakReference<>(client);
        client.voidToVoid();
        assertThat(requestPaths).containsExactly("/foo1/voidToVoid");

        Counter activeDnsRefreshTasks =
                DialogueDnsDiscoveryMetrics.of(registry).scheduled("dialogue-nonreloading-SampleServiceBlocking");
        assertThat(activeDnsRefreshTasks.getCount()).isEqualTo(1L);
        assertThat(clientWeakRef.get())
                .as("This reference is expected to be present before the strong client ref is unset")
                .isNotNull();
        // This reference _must_ be freed to allow the cleaner to do its job.
        client = null;
        attemptToGarbageCollect(clientWeakRef);
        Awaitility.waitAtMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(activeDnsRefreshTasks.getCount())
                .isEqualTo(0L));
    }

    @Test
    void legacyStaticFactoryDnsTaskCleanup() {
        List<String> requestPaths = new CopyOnWriteArrayList<>();
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(200);
        };

        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        SSLSocketFactory socketFactory = SslSocketFactories.createSslSocketFactory(TestConfigurations.SSL_CONFIG);
        X509TrustManager trustManager = SslSocketFactories.createX509TrustManager(TestConfigurations.SSL_CONFIG);

        ClientConfiguration config = ClientConfiguration.builder()
                .from(ClientConfigurations.of(
                        ImmutableList.of(getUri(undertow) + FOO_PATH),
                        socketFactory,
                        trustManager,
                        TestConfigurations.AGENT))
                .taggedMetricRegistry(registry)
                .build();
        @SuppressWarnings("unused")
        SampleServiceBlocking client = DialogueClients.create(SampleServiceBlocking.class, config);
        WeakReference<SampleServiceBlocking> clientWeakRef = new WeakReference<>(client);
        client.voidToVoid();
        assertThat(requestPaths).containsExactly("/foo1/voidToVoid");
        Counter activeDnsRefreshTasks =
                DialogueDnsDiscoveryMetrics.of(registry).scheduled("dialogue-nonreloading-SampleServiceBlocking");
        assertThat(activeDnsRefreshTasks.getCount()).isEqualTo(1L);
        assertThat(clientWeakRef.get())
                .as("This reference is expected to be present before the strong client ref is unset")
                .isNotNull();
        // This reference _must_ be freed to allow the cleaner to do its job.
        client = null;
        attemptToGarbageCollect(clientWeakRef);
        Awaitility.waitAtMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(activeDnsRefreshTasks.getCount())
                .isEqualTo(0L));
    }

    private static void attemptToGarbageCollect(WeakReference<?> sentinel) {
        Awaitility.waitAtMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            attemptToGarbageCollect();
            assertThat(sentinel.get()).isNull();
        });
    }

    private static void attemptToGarbageCollect() {
        // Create some garbage to entice the collector
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (baos.toString(StandardCharsets.UTF_8).length() < 4096) {
            byte[] buf = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            baos.write(buf, 0, buf.length);
        }
        // System.gc is disabled in some environments, so it alone cannot be relied upon.
        System.gc();
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format(
                "%s://localhost:%d",
                listenerInfo.getProtcol(), ((InetSocketAddress) listenerInfo.getAddress()).getPort());
    }
}
