/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import javax.net.ssl.SSLContext;

/**
 * Manual tests to verify socks proxies work as expected.
 * Run {@code ssh -D 8081 -v -N localhost} to set up a local socks proxy on 8081.
 * Then, run the tests. You should see logging in the socks proxy output when the
 * test runs, otherwise the proxy isn't being used. If you terminate the proxy,
 * both tests should fail.
 */
final class SocksProxyManualTest {

    void testTls() throws Exception {
        // ssh -D 8081 -v -N localhost
        System.setProperty("dialogue.experimental.socks5.proxy", "127.0.0.1:8081");
        SSLContext context = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        Undertow undertow = Undertow.builder()
                .addHttpsListener(8080, null, context, new ResponseCodeHandler(204))
                .build();
        undertow.start();
        try {
            ClientConfiguration configuration = TestConfigurations.create("https://localhost:" + 8080);
            Channel channel = ApacheHttpClientChannels.create(configuration, "test");
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            try (Response response = future.get()) {
                assertThat(response.code()).isEqualTo(204);
            }
        } finally {
            undertow.stop();
        }
    }

    void testPlain() throws Exception {
        // ssh -D 8081 -v -N localhost
        System.setProperty("dialogue.experimental.socks5.proxy", "127.0.0.1:8081");
        Undertow undertow = Undertow.builder()
                .addHttpListener(8080, null, new ResponseCodeHandler(204))
                .build();
        undertow.start();
        try {
            ClientConfiguration configuration = TestConfigurations.create("http://localhost:" + 8080);
            Channel channel = ApacheHttpClientChannels.create(configuration, "test");
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            try (Response response = future.get()) {
                assertThat(response.code()).isEqualTo(204);
            }
        } finally {
            undertow.stop();
        }
    }
}
