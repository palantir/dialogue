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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DefaultFactoryTest {
    ServiceConfiguration serviceConfig;
    private Undertow undertow;
    private HttpHandler undertowHandler;

    @BeforeEach
    public void before() {
        undertow = Undertow.builder()
                .addHttpListener(
                        0, "localhost", new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();
        serviceConfig = ServiceConfiguration.builder()
                .security(TestConfigurations.SSL_CONFIG)
                .addUris(getUri(undertow))
                .build();
    }

    @AfterEach
    public void after() {
        undertow.stop();
    }

    @Test
    void throws_if_user_agent_is_missing() {
        assertThatThrownBy(() -> DialogueClients.create().getNonReloading(SampleServiceAsync.class, serviceConfig))
                .hasMessageContaining("userAgent must be specified");
    }

    @Test
    void building_non_reloading_clients_always_gives_the_same_instance() {
        AtomicInteger statusCode = new AtomicInteger(200);
        Set<String> requestPaths = new HashSet<>();
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

        DialogueClients.Factory factory = DialogueClients.create()
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

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format("%s:/%s", listenerInfo.getProtcol(), listenerInfo.getAddress());
    }
}
