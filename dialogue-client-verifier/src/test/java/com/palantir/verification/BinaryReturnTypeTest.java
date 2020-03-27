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

package com.palantir.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BinaryReturnTypeTest {
    private static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("BinaryReturnTypeTest", "0.0.0"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("../dialogue-core/src/test/resources/trustStore.jks"),
            Paths.get("../dialogue-core/src/test/resources/keyStore.jks"),
            "keystore");

    private Undertow undertow;
    private HttpHandler undertowHandler;

    @Before
    public void before() {
        undertow = Undertow.builder()
                .addHttpListener(0, "localhost", new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();
    }

    @Test
    public void conjure_generated_interface_with_optional_binary_return_type_and_gzip() {
        undertowHandler = exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
            exchange.getResponseHeaders().put(Headers.CONTENT_ENCODING, "gzip");
            exchange.getOutputStream().write(gzipCompress("Hello, world"));
        };

        SampleServiceAsync client = SampleServiceAsync.of(
                ApacheHttpClientChannels.create(clientConf(getUri(undertow))),
                DefaultConjureRuntime.builder().build());

        ListenableFuture<Optional<InputStream>> future = client.getOptionalBinary();
        Optional<InputStream> maybeBinary = Futures.getUnchecked(future);

        assertThat(maybeBinary).isPresent();
        assertThat(maybeBinary.get()).hasSameContentAs(asInputStream("Hello, world"));
    }

    @After
    public void after() {
        undertow.stop();
    }

    private static ByteArrayInputStream asInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    private static ClientConfiguration clientConf(String uri) {
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .addUris(uri)
                        .security(SSL_CONFIG)
                        .readTimeout(Duration.ofSeconds(1))
                        .writeTimeout(Duration.ofSeconds(1))
                        .connectTimeout(Duration.ofSeconds(1))
                        .build()))
                .userAgent(USER_AGENT)
                .build();
    }

    private static byte[] gzipCompress(String stringToCompress) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gzipOutput = new GZIPOutputStream(baos)) {
            gzipOutput.write(stringToCompress.getBytes(StandardCharsets.UTF_8));
            gzipOutput.finish();
            return baos.toByteArray();
        }
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format("%s:/%s", listenerInfo.getProtcol(), listenerInfo.getAddress());
    }
}
