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

package com.palantir.dialogue;

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.net.InetSocketAddress;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class UndertowServer implements BeforeEachCallback, AfterEachCallback {

    private Undertow undertow;
    private volatile HttpHandler undertowHandler;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        undertow = Undertow.builder()
                .addHttpsListener(
                        0,
                        "localhost",
                        sslContext,
                        new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();
    }

    @Override
    public void afterEach(ExtensionContext _context) {
        undertow.stop();
    }

    public void setHandler(HttpHandler handler) {
        this.undertowHandler = handler;
    }

    public String getUri() {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format(
                "%s://localhost:%d",
                listenerInfo.getProtcol(), ((InetSocketAddress) listenerInfo.getAddress()).getPort());
    }
}
