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

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestOnlyCertificates;
import com.palantir.dialogue.core.DialogueChannelFactory.ChannelArgs;
import com.palantir.dialogue.hc5.ApacheHttpClientChannels.CloseableClient;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.junit.jupiter.api.Test;

final class ResolvedAddressTest {

    @Test
    void testPlainHttp() throws Exception {
        Undertow undertow = Undertow.builder()
                .addHttpListener(0, null, new ResponseCodeHandler(204))
                .build();
        undertow.start();
        try (CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(TestConfigurations.create(), "test")) {
            int port = port(undertow);
            String hostname = UUID.randomUUID().toString();
            String uri = "http://" + hostname + ':' + port;
            InetAddress resolved = InetAddress.getByAddress(hostname, new byte[] {127, 0, 0, 1});
            Channel channel = ApacheHttpClientChannels.createSingleUri(
                    ChannelArgs.builder().uri(uri).resolvedAddress(resolved).build(), client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            try (Response response = future.get()) {
                assertThat(response.code()).isEqualTo(204);
            }
        } finally {
            undertow.stop();
        }
    }

    @Test
    void testTls() throws Exception {
        String hostname = UUID.randomUUID().toString();

        TestOnlyCertificates.GeneratedKeyPair keyPair = TestOnlyCertificates.generate(hostname);

        SSLContext context = TestOnlyCertificates.toContext(keyPair, true);

        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create())
                .sslSocketFactory(context.getSocketFactory())
                .trustManager(TestOnlyCertificates.toTrustManager(keyPair))
                .build();

        Undertow undertow = Undertow.builder()
                .addHttpsListener(0, null, context, new ResponseCodeHandler(204))
                .build();
        undertow.start();
        try (CloseableClient client = ApacheHttpClientChannels.createCloseableHttpClient(config, "test")) {
            int port = port(undertow);
            String uri = "https://" + hostname + ':' + port;
            InetAddress resolved = InetAddress.getByAddress(hostname, new byte[] {127, 0, 0, 1});
            Channel channel = ApacheHttpClientChannels.createSingleUri(
                    ChannelArgs.builder().uri(uri).resolvedAddress(resolved).build(), client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            try (Response response = future.get()) {
                assertThat(response.code()).isEqualTo(204);
            }
        } finally {
            undertow.stop();
        }
    }

    @Test
    void testTlsWithUnexpectedHostname() throws Exception {
        String hostname = UUID.randomUUID().toString();
        TestOnlyCertificates.GeneratedKeyPair keyPair = TestOnlyCertificates.generate("localhost");

        SSLContext context = TestOnlyCertificates.toContext(keyPair, true);

        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create())
                .sslSocketFactory(context.getSocketFactory())
                .trustManager(TestOnlyCertificates.toTrustManager(keyPair))
                .build();

        Undertow undertow = Undertow.builder()
                .addHttpsListener(0, null, context, new ResponseCodeHandler(204))
                .build();
        undertow.start();
        try (CloseableClient client = ApacheHttpClientChannels.createCloseableHttpClient(config, "test")) {
            int port = port(undertow);
            String uri = "https://" + hostname + ':' + port;
            InetAddress resolved = InetAddress.getByAddress(hostname, new byte[] {127, 0, 0, 1});
            Channel channel = ApacheHttpClientChannels.createSingleUri(
                    ChannelArgs.builder().uri(uri).resolvedAddress(resolved).build(), client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            assertThat(future)
                    .failsWithin(Duration.ofSeconds(5))
                    .withThrowableThat()
                    .withRootCauseInstanceOf(SSLPeerUnverifiedException.class);
        } finally {
            undertow.stop();
        }
    }

    private static int port(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }
}
