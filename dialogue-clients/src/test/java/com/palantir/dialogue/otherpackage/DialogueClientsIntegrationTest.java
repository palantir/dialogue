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

package com.palantir.dialogue.otherpackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DialogueClientsIntegrationTest {
    private static final String FOO_SERVICE = "foo";
    private ServiceConfiguration serviceConfig;
    private Undertow undertow;
    private HttpHandler undertowHandler;
    private ServicesConfigBlock scb;
    private PartialServiceConfiguration foo1;
    private PartialServiceConfiguration foo2;
    private PartialServiceConfiguration threeFoos;
    private final String foo1Path = "/foo1";
    private final String foo2Path = "/foo2";
    private final String foo3Path = "/foo3";

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
        serviceConfig = ServiceConfiguration.builder()
                .security(TestConfigurations.SSL_CONFIG)
                .addUris(getUri(undertow))
                .build();
        foo1 = PartialServiceConfiguration.builder()
                .addUris(getUri(undertow) + foo1Path)
                .build();
        foo2 = PartialServiceConfiguration.builder()
                .addUris(getUri(undertow) + foo2Path)
                .build();
        threeFoos = PartialServiceConfiguration.builder()
                .addUris(getUri(undertow) + foo1Path)
                .addUris(getUri(undertow) + foo2Path)
                .addUris(getUri(undertow) + foo3Path)
                .build();
        scb = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices("foo", foo1)
                .build();
    }

    @AfterEach
    public void after() {
        undertow.stop();
    }

    @Test
    void throws_if_user_agent_is_missing() {
        assertThatThrownBy(() -> DialogueClients.create(Refreshable.only(null))
                        .getNonReloading(SampleServiceAsync.class, serviceConfig))
                .hasMessageContaining("userAgent must be specified");
    }

    @Test
    void reload_uris_works() {
        List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(200);
        };

        SettableRefreshable<ServicesConfigBlock> refreshable = Refreshable.create(scb);
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(refreshable).withUserAgent(TestConfigurations.AGENT);

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "foo");
        client.voidToVoid();

        refreshable.update(
                ServicesConfigBlock.builder().from(scb).putServices("foo", foo2).build());

        client.voidToVoid();
        assertThat(requestPaths).containsExactly("/foo1/voidToVoid", "/foo2/voidToVoid");
    }

    @Test
    void building_non_reloading_clients_always_gives_the_same_instance() {
        AtomicInteger statusCode = new AtomicInteger(200);
        Set<String> requestPaths = ConcurrentHashMap.newKeySet();
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(statusCode.get());
        };

        serviceConfig = ServiceConfiguration.builder()
                .security(TestConfigurations.SSL_CONFIG)
                .addUris(IntStream.range(0, 100)
                        .mapToObj(i -> getUri(undertow) + "/api" + i)
                        .toArray(String[]::new))
                .maxNumRetries(0)
                .build();

        DialogueClients.ReloadingFactory factory = DialogueClients.create(Refreshable.only(null))
                .withUserAgent(TestConfigurations.AGENT)
                .withNodeSelectionStrategy(NodeSelectionStrategy.PIN_UNTIL_ERROR);

        SampleServiceBlocking instance = factory.getNonReloading(SampleServiceBlocking.class, serviceConfig);
        SampleServiceBlocking instance2 = factory.getNonReloading(SampleServiceBlocking.class, serviceConfig);
        SampleServiceBlocking instance3 = factory.getNonReloading(SampleServiceBlocking.class, serviceConfig);
        instance.voidToVoid();
        instance2.voidToVoid();
        instance3.voidToVoid();
        assertThat(requestPaths)
                .describedAs("Out of the hundred urls, each of these clients should start off pinned to the same host")
                .hasSize(1);
        statusCode.set(503);
        assertThatThrownBy(instance::voidToVoid).isInstanceOf(QosException.Unavailable.class);
        assertThatThrownBy(instance2::voidToVoid).isInstanceOf(QosException.Unavailable.class);
        assertThatThrownBy(instance3::voidToVoid).isInstanceOf(QosException.Unavailable.class);
        assertThat(requestPaths).hasSize(3);
    }

    @Test
    void test_conn_timeout_with_unlimited_socket_timeout() {
        undertowHandler = _exchange -> Thread.sleep(1_000L);
        SampleServiceBlocking client = DialogueClients.create(Refreshable.only(null))
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0)
                .getNonReloading(
                        SampleServiceBlocking.class,
                        ServiceConfiguration.builder()
                                .addUris(getUri(undertow))
                                .security(TestConfigurations.SSL_CONFIG)
                                .connectTimeout(Duration.ofMillis(300))
                                // socket timeouts use zero as a sentinel for unlimited duration
                                .readTimeout(Duration.ZERO)
                                .writeTimeout(Duration.ZERO)
                                .build());
        assertThatCode(client::voidToVoid)
                .as("initial request should not throw")
                .doesNotThrowAnyException();
        assertThatCode(client::voidToVoid)
                .as("subsequent requests reusing the connection should not throw")
                .doesNotThrowAnyException();
    }

    @Test
    void test_unlimited_timeouts() {
        undertowHandler = _exchange -> Thread.sleep(1_000L);
        SampleServiceBlocking client = DialogueClients.create(Refreshable.only(null))
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0)
                .getNonReloading(
                        SampleServiceBlocking.class,
                        ServiceConfiguration.builder()
                                .addUris(getUri(undertow))
                                .security(TestConfigurations.SSL_CONFIG)
                                // socket timeouts use zero as a sentinel for unlimited duration
                                .connectTimeout(Duration.ZERO)
                                .readTimeout(Duration.ZERO)
                                .writeTimeout(Duration.ZERO)
                                .build());
        assertThatCode(client::voidToVoid)
                .as("initial request should not throw")
                .doesNotThrowAnyException();
        assertThatCode(client::voidToVoid)
                .as("subsequent requests reusing the connection should not throw")
                .doesNotThrowAnyException();
    }

    @Test
    void test_per_host_routes_correctly() {
        List<String> requestPaths = Collections.synchronizedList(new ArrayList<>());
        undertowHandler = exchange -> {
            requestPaths.add(exchange.getRequestPath());
            exchange.setStatusCode(200);
        };

        SettableRefreshable<ServicesConfigBlock> refreshable = Refreshable.create(ServicesConfigBlock.builder()
                .from(scb)
                .putServices(FOO_SERVICE, threeFoos)
                .build());
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(refreshable).withUserAgent(TestConfigurations.AGENT);

        Refreshable<List<SampleServiceBlocking>> refreshableClients =
                factory.perHost(FOO_SERVICE).getPerHost(SampleServiceBlocking.class);
        List<SampleServiceBlocking> perHostClients = refreshableClients.get();
        assertThat(perHostClients).hasSize(3);

        for (int i = 0; i < perHostClients.size(); i++) {
            perHostClients.get(i).stringToVoid(Integer.toString(i));
        }
        assertThat(requestPaths)
                .containsExactly("/foo1/stringToVoid/0", "/foo2/stringToVoid/1", "/foo3/stringToVoid/2");
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format(
                "%s://localhost:%d",
                listenerInfo.getProtcol(), ((InetSocketAddress) listenerInfo.getAddress()).getPort());
    }
}
