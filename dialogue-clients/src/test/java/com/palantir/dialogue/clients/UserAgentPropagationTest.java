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

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import com.palantir.tracing.undertow.TracedStateHandler;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UserAgentPropagationTest {
    private final List<Undertow> servers = new ArrayList<>();

    @AfterEach
    void after() {
        servers.forEach(Undertow::stop);
    }

    @Test
    void downstream_sees_caller_user_agent_and_originating_for_user_agent() {
        AtomicReference<String> userAgentAtC = new AtomicReference<>();
        AtomicReference<String> forUserAgentAtC = new AtomicReference<>();
        Undertow serviceC = startServer(exchange -> {
            userAgentAtC.set(exchange.getRequestHeaders().getFirst(Headers.USER_AGENT));
            forUserAgentAtC.set(exchange.getRequestHeaders().getFirst("For-User-Agent"));
        });

        SampleServiceBlocking clientBToC = clientFactory(serviceC, "service-b", "2.0.0", "owningRid2")
                .get(SampleServiceBlocking.class, "downstream");
        // TracedStateHandler initializes the inbound trace the same way conjure-undertow (and witchcraft) servers do,
        // capturing For-User-Agent, falling back to User-Agent when absent.
        Undertow serviceB = startServer(new TracedStateHandler(_exchange -> clientBToC.voidToVoid()));

        SampleServiceBlocking clientAToB = clientFactory(serviceB, "service-a", "1.0.0", "owningRid1")
                .get(SampleServiceBlocking.class, "downstream");
        clientAToB.voidToVoid();

        assertThat(userAgentAtC.get())
                .as("User-Agent is set per hop by the calling client")
                .startsWith("service-b/2.0.0 (owningRid2) ");
        assertThat(forUserAgentAtC.get())
                .as("For-User-Agent carries the originating user agent through the trace")
                .startsWith("service-a/1.0.0 (owningRid1) ");
    }

    private Undertow startServer(HttpHandler handler) {
        Undertow undertow = Undertow.builder()
                .addHttpsListener(
                        0,
                        "localhost",
                        SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG),
                        new BlockingHandler(handler))
                .build();
        undertow.start();
        servers.add(undertow);
        return undertow;
    }

    private static DialogueClients.ReloadingFactory clientFactory(
            Undertow target, String name, String version, String comment) {
        ServicesConfigBlock scb = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        "downstream",
                        PartialServiceConfiguration.builder()
                                .addUris(getUri(target))
                                .build())
                .build();
        return DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(UserAgent.of(UserAgent.Agent.of(name, version, List.of(comment))));
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format(
                "%s://localhost:%d",
                listenerInfo.getProtcol(), ((InetSocketAddress) listenerInfo.getAddress()).getPort());
    }
}
