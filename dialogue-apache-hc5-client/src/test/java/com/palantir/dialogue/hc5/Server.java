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

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Server {
    static {
        LoggerBindings.initialize();
    }

    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private static final byte[] responseData =
            ('"' + Strings.repeat("Hello, World!", 1024) + '"').getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        SSLContext sslContext = SslSocketFactories.createSslContext(SslConfiguration.of(
                Paths.get("src/test/resources/trustStore.jks"),
                Paths.get("src/test/resources/keyStore.jks"),
                "keystore"));
        AtomicLong requests = new AtomicLong();
        Undertow server = Undertow.builder()
                //                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .addHttpsListener(8443, null, sslContext)
                .setHandler(new BlockingHandler(exchange -> {
                    long current = requests.incrementAndGet();
                    if (current % 1000 == 0) {
                        log.info("Received {} requests", current);
                    }
                    //                    if (!Protocols.HTTP_2_0.equals(exchange.getProtocol())) {
                    //                        log.error("Bad protocol: {}", exchange.getProtocol());
                    //                    }
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
                    ByteStreams.copy(exchange.getInputStream(), NilOutputStream.INSTANCE);
                    exchange.getResponseSender().send(ByteBuffer.wrap(responseData));
                }))
                .build();
        server.start();
    }

    private static final class NilOutputStream extends OutputStream {
        private static final OutputStream INSTANCE = new NilOutputStream();

        @Override
        public void write(int b) throws IOException {}

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {}
    }
}
