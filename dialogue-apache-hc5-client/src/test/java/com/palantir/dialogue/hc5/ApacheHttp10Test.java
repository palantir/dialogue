/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.util.Protocols;
import java.net.InetSocketAddress;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ApacheHttp10Test {

    private Channel create(ClientConfiguration config) {
        return ApacheHttpClientChannels.create(config, "test");
    }

    private Undertow server;

    private static int getPort(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }

    @BeforeEach
    public void beforeEach() {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(8)
                .setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, false)
                .setHandler(exchange -> {
                    exchange.setProtocol(Protocols.HTTP_1_0);
                    exchange.setStatusCode(200);
                    exchange.endExchange();
                    exchange.getConnection().close();
                })
                .addHttpsListener(0, null, sslContext)
                .build();
        server.start();
    }

    @AfterEach
    public void afterEach() {
        server.stop();
    }

    @Test
    public void testHandshakeLongerThanConnectDoesNotTimeout() throws Exception {
        int serverPort = getPort(server);
        Channel channel = create(TestConfigurations.create("https://localhost:" + serverPort));
        // Make multiple requests to ensure connections aren't reused incorrectly.
        for (int i = 0; i < 3; i++) {
            try (Response response = channel.execute(
                            TestEndpoint.POST, Request.builder().build())
                    .get()) {
                assertThat(response.code()).isEqualTo(200);
            }
        }
    }
}
