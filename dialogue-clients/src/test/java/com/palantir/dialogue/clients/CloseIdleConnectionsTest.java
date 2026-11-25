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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import io.undertow.Undertow;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.BlockingHandler;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloseIdleConnectionsTest {
    private static final String SERVICE = "test-service";

    /** The server connection that served each request, in order. */
    private final List<ServerConnection> connectionPerRequest = new CopyOnWriteArrayList<>();

    private final Set<ServerConnection> closedConnections = ConcurrentHashMap.newKeySet();
    private Undertow undertow;
    private ReloadingFactory factory;

    @BeforeEach
    void before() {
        undertow = Undertow.builder()
                .addHttpsListener(
                        0,
                        "localhost",
                        SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG),
                        new BlockingHandler(exchange -> {
                            ServerConnection connection = exchange.getConnection();
                            if (!connectionPerRequest.contains(connection)) {
                                connection.addCloseListener(closedConnections::add);
                            }
                            connectionPerRequest.add(connection);
                            exchange.setStatusCode(204);
                        }))
                .build();
        undertow.start();
        InetSocketAddress address = (InetSocketAddress)
                Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress();
        ServicesConfigBlock scb = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        SERVICE,
                        PartialServiceConfiguration.builder()
                                .addUris("https://localhost:" + address.getPort() + "/api")
                                .build())
                .build();
        factory = DialogueClients.create(Refreshable.only(scb)).withUserAgent(TestConfigurations.AGENT);
    }

    @AfterEach
    void after() {
        undertow.stop();
    }

    @Test
    void pooled_connection_is_reused_without_close() {
        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, SERVICE);

        client.voidToVoid();
        client.voidToVoid();

        assertThat(connectionPerRequest).hasSize(2);
        assertThat(connectionPerRequest.get(1)).isSameAs(connectionPerRequest.get(0));
    }

    @Test
    void close_idle_connections_closes_the_pooled_connection_of_the_service() {
        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, SERVICE);
        client.voidToVoid();
        ServerConnection first = connectionPerRequest.get(0);

        factory.closeIdleConnections(SERVICE);

        await().atMost(Duration.ofSeconds(10)).until(() -> closedConnections.contains(first));
        client.voidToVoid();
        assertThat(connectionPerRequest).hasSize(2);
        assertThat(connectionPerRequest.get(1))
                .as("the next request opens a new connection")
                .isNotSameAs(first);
    }

    @Test
    void close_idle_connections_does_nothing_for_an_unknown_service() {
        assertThatCode(() -> factory.closeIdleConnections("not-a-service")).doesNotThrowAnyException();
    }
}
