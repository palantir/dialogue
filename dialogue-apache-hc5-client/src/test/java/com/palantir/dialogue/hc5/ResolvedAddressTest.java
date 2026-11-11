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

package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableSet;
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
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.junit.jupiter.api.Test;

final class ResolvedAddressTest {

    @Test
    void testPlainHttp() throws Exception {
        String clientName = "test";
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create())
                .taggedMetricRegistry(registry)
                .build();
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        Timer connectionCreateTimer = metrics.connectionCreate()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionCreate_Result.SUCCESS)
                .address(DialogueClientMetrics.ConnectionCreate_Address.PRE_RESOLVED)
                .build();
        Timer connectionConnectTimer = metrics.connectionConnect()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionConnect_Result.SUCCESS)
                .address(DialogueClientMetrics.ConnectionConnect_Address.PRE_RESOLVED)
                .build();
        Undertow undertow = Undertow.builder()
                .addHttpListener(0, null, new ResponseCodeHandler(204))
                .build();
        undertow.start();
        try (CloseableClient client = ApacheHttpClientChannels.createCloseableHttpClient(config, clientName)) {
            int port = port(undertow);
            String hostname = UUID.randomUUID().toString();
            String uri = "http://" + hostname + ':' + port;
            InetAddress resolved = InetAddress.getByAddress(hostname, new byte[] {127, 0, 0, 1});
            Channel channel = ApacheHttpClientChannels.createSingleUri(
                    ChannelArgs.builder().uri(uri).resolvedAddress(resolved).build(), client);

            assertThat(connectionCreateTimer.getCount()).isZero();
            assertThat(connectionConnectTimer.getCount()).isZero();

            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            try (Response response = future.get()) {
                assertThat(response.code()).isEqualTo(204);
            }
        } finally {
            undertow.stop();
        }
        assertThat(connectionCreateTimer.getCount()).isOne();
        assertThat(connectionConnectTimer.getCount()).isOne();
    }

    @Test
    void testTls() throws Exception {
        String clientName = "test";
        String hostname = UUID.randomUUID().toString();
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        Timer connectionCreateTimer = metrics.connectionCreate()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionCreate_Result.SUCCESS)
                .address(DialogueClientMetrics.ConnectionCreate_Address.PRE_RESOLVED)
                .build();
        Timer connectionConnectTimer = metrics.connectionConnect()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionConnect_Result.SUCCESS)
                .address(DialogueClientMetrics.ConnectionConnect_Address.PRE_RESOLVED)
                .build();
        TestOnlyCertificates.GeneratedKeyPair keyPair = TestOnlyCertificates.generate(hostname);

        SSLContext context = TestOnlyCertificates.toContext(keyPair, true);

        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create())
                .sslSocketFactory(context.getSocketFactory())
                .trustManager(TestOnlyCertificates.toTrustManager(keyPair))
                .taggedMetricRegistry(registry)
                .build();

        Undertow undertow = Undertow.builder()
                .addHttpsListener(0, null, context, new ResponseCodeHandler(204))
                .build();
        undertow.start();
        try (CloseableClient client = ApacheHttpClientChannels.createCloseableHttpClient(config, clientName)) {
            int port = port(undertow);
            String uri = "https://" + hostname + ':' + port;
            InetAddress resolved = InetAddress.getByAddress(hostname, new byte[] {127, 0, 0, 1});
            Channel channel = ApacheHttpClientChannels.createSingleUri(
                    ChannelArgs.builder().uri(uri).resolvedAddress(resolved).build(), client);
            assertThat(connectionCreateTimer.getCount()).isZero();
            assertThat(connectionConnectTimer.getCount()).isZero();
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            try (Response response = future.get()) {
                assertThat(response.code()).isEqualTo(204);
            }
        } finally {
            undertow.stop();
        }
        assertThat(connectionCreateTimer.getCount()).isOne();
        assertThat(connectionConnectTimer.getCount()).isOne();
    }

    @Test
    void testTlsWithUnexpectedHostname() throws Exception {
        String clientName = "test";
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        Timer connectionConnectSuccessTimer = metrics.connectionConnect()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionConnect_Result.SUCCESS)
                .address(DialogueClientMetrics.ConnectionConnect_Address.PRE_RESOLVED)
                .build();
        Timer connectionConnectFailureTimer = metrics.connectionConnect()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionConnect_Result.FAILURE)
                .address(DialogueClientMetrics.ConnectionConnect_Address.PRE_RESOLVED)
                .build();
        String hostname = UUID.randomUUID().toString();
        TestOnlyCertificates.GeneratedKeyPair keyPair = TestOnlyCertificates.generate("localhost");

        SSLContext context = TestOnlyCertificates.toContext(keyPair, true);

        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create())
                .sslSocketFactory(context.getSocketFactory())
                .trustManager(TestOnlyCertificates.toTrustManager(keyPair))
                .taggedMetricRegistry(registry)
                .build();

        Undertow undertow = Undertow.builder()
                .addHttpsListener(0, null, context, new ResponseCodeHandler(204))
                .build();
        undertow.start();
        try (CloseableClient client = ApacheHttpClientChannels.createCloseableHttpClient(config, clientName)) {
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
        // The socket connected before the TLS handshake failed, so this is a successful connect.
        assertThat(connectionConnectSuccessTimer.getCount()).isOne();
        assertThat(connectionConnectFailureTimer.getCount()).isZero();
    }

    @Test
    void testConnectFailure() throws Exception {
        String clientName = "test";
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create())
                .taggedMetricRegistry(registry)
                .build();
        Timer connectionConnectFailureTimer = DialogueClientMetrics.of(registry)
                .connectionConnect()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionConnect_Result.FAILURE)
                .address(DialogueClientMetrics.ConnectionConnect_Address.PRE_RESOLVED)
                .build();
        int closedPort = closedPort();
        try (CloseableClient client = ApacheHttpClientChannels.createCloseableHttpClient(config, clientName)) {
            String hostname = UUID.randomUUID().toString();
            String uri = "http://" + hostname + ':' + closedPort;
            InetAddress resolved = InetAddress.getByAddress(hostname, new byte[] {127, 0, 0, 1});
            Channel channel = ApacheHttpClientChannels.createSingleUri(
                    ChannelArgs.builder().uri(uri).resolvedAddress(resolved).build(), client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            assertThat(future)
                    .failsWithin(Duration.ofSeconds(5))
                    .withThrowableThat()
                    .withRootCauseInstanceOf(ConnectException.class);
        }
        assertThat(connectionConnectFailureTimer.getCount()).isOne();
    }

    @Test
    void testConnectFailureOnEarlierAddress() throws Exception {
        String clientName = "test";
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        ClientConfiguration config = ClientConfiguration.builder()
                .from(TestConfigurations.create())
                .taggedMetricRegistry(registry)
                .build();
        DialogueClientMetrics metrics = DialogueClientMetrics.of(registry);
        Timer connectionConnectSuccessTimer = metrics.connectionConnect()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionConnect_Result.SUCCESS)
                .address(DialogueClientMetrics.ConnectionConnect_Address.DNS_LOOKUP)
                .build();
        Timer connectionConnectFailureTimer = metrics.connectionConnect()
                .clientName(clientName)
                .result(DialogueClientMetrics.ConnectionConnect_Result.FAILURE)
                .address(DialogueClientMetrics.ConnectionConnect_Address.DNS_LOOKUP)
                .build();
        Undertow undertow = Undertow.builder()
                .addHttpListener(0, "127.0.0.1", new ResponseCodeHandler(204))
                .build();
        undertow.start();
        String hostname = UUID.randomUUID().toString();
        // Nothing listens on the IPv6 loopback, so the first address fails and the client moves to the second.
        ImmutableSet<InetAddress> addresses = ImmutableSet.of(
                InetAddress.getByAddress(hostname, new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}),
                InetAddress.getByAddress(hostname, new byte[] {127, 0, 0, 1}));
        try (CloseableClient client = ApacheHttpClientChannels.clientBuilder()
                .clientConfiguration(config)
                .clientName(clientName)
                .dnsResolver(_hostname -> addresses)
                .build()) {
            Channel channel = ApacheHttpClientChannels.createSingleUri(
                    ChannelArgs.builder()
                            .uri("http://" + hostname + ':' + port(undertow))
                            .build(),
                    client);
            ListenableFuture<Response> future =
                    channel.execute(TestEndpoint.GET, Request.builder().build());
            try (Response response = future.get()) {
                assertThat(response.code()).isEqualTo(204);
            }
        } finally {
            undertow.stop();
        }
        assertThat(connectionConnectFailureTimer.getCount()).isOne();
        assertThat(connectionConnectSuccessTimer.getCount()).isOne();
    }

    private static int closedPort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static int port(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }
}
