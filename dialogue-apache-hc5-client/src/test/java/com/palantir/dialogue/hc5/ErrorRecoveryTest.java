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
package com.palantir.dialogue.hc5;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import io.undertow.Undertow;
import io.undertow.server.handlers.ResponseCodeHandler;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

@SuppressWarnings("checkstyle:NestedTryDepth")
public final class ErrorRecoveryTest {

    @Test
    public void errorRecovery() throws Exception {
        Clients clients = DefaultConjureRuntime.builder().build().clients();
        Undertow server = startServer();
        try {
            String uri = "https://localhost:" + getPort(server);
            ClientConfiguration conf = TestConfigurations.create(uri);
            try (ApacheHttpClientChannels.CloseableClient client =
                    ApacheHttpClientChannels.createCloseableHttpClient(conf, "client")) {
                Channel channel = ApacheHttpClientChannels.createSingleUri(uri, client);
                EndpointChannel endpointChannel = request -> channel.execute(TestEndpoint.POST, request);
                assertThatThrownBy(() ->
                                singleRequest(clients, endpointChannel, Optional.of(ErrorThrowingRequestBody.INSTANCE)))
                        .isInstanceOf(StackOverflowError.class);
                // Following a failure, the client should be operable.
                singleRequest(clients, endpointChannel, Optional.empty());
            }
        } finally {
            server.stop();
        }
    }

    private static void singleRequest(Clients clients, EndpointChannel endpointChannel, Optional<RequestBody> body) {
        clients.callBlocking(endpointChannel, Request.builder().body(body).build(), VoidDeserializer.INSTANCE);
    }

    private static Undertow startServer() {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        Undertow server = Undertow.builder()
                .setHandler(ResponseCodeHandler.HANDLE_200)
                .addHttpsListener(0, null, sslContext)
                .build();
        server.start();
        return server;
    }

    private static int getPort(Undertow undertow) {
        return ((InetSocketAddress)
                        Iterables.getOnlyElement(undertow.getListenerInfo()).getAddress())
                .getPort();
    }

    private enum VoidDeserializer implements Deserializer<Void> {
        INSTANCE;

        @Override
        public Void deserialize(Response response) {
            response.close();
            return null;
        }

        @Override
        public Optional<String> accepts() {
            return Optional.empty();
        }
    }

    private enum ErrorThrowingRequestBody implements RequestBody {
        INSTANCE;

        @Override
        public void writeTo(OutputStream _output) {
            throw new StackOverflowError();
        }

        @Override
        public String contentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean repeatable() {
            return false;
        }

        @Override
        public void close() {}
    }
}
