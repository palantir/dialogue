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

package com.palantir.dialogue.otherpackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
            String uri = getUri(undertow).replace("localhost", randomHostname);
            assertThat(URI.create(uri)).hasHost(randomHostname);
            ReloadingFactory factory = DialogueClients.create(Refreshable.only(ServicesConfigBlock.builder()
                            .defaultSecurity(TestConfigurations.SSL_CONFIG)
                            .putServices(
                                    "foo",
                                    PartialServiceConfiguration.builder()
                                            .addUris(uri)
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

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format(
                "%s://localhost:%d",
                listenerInfo.getProtcol(), ((InetSocketAddress) listenerInfo.getAddress()).getPort());
    }
}
