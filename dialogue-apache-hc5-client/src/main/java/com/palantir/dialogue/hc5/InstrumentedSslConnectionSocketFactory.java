/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Timer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Timeout;

/**
 * InstrumentedSslConnectionSocketFactory extends {@link SSLConnectionSocketFactory} for a couple minor features.
 * <ol>
 *     <li>{@link #rawSocketCreator} provided for socks proxy support.</li>
 *     <li>{@link #connectSocket(Socket, InetSocketAddress, Timeout, HttpContext)}
 *     overridden to add timing metrics around {@link Socket#connect(SocketAddress, int)}</li>
 * </ol>
 */
final class InstrumentedSslConnectionSocketFactory extends SSLConnectionSocketFactory {
    private final Supplier<Socket> rawSocketCreator;

    private final ConnectInstrumentation connectInstrumentation;

    InstrumentedSslConnectionSocketFactory(
            ConnectInstrumentation connectInstrumentation,
            SSLSocketFactory socketFactory,
            String[] supportedProtocols,
            String[] supportedCipherSuites,
            HostnameVerifier hostnameVerifier,
            Supplier<Socket> rawSocketCreator) {
        super(socketFactory, supportedProtocols, supportedCipherSuites, hostnameVerifier);
        this.connectInstrumentation = connectInstrumentation;
        this.rawSocketCreator = rawSocketCreator;
    }

    @Override
    public Socket createSocket(HttpContext _context) {
        return rawSocketCreator.get();
    }

    @Override
    protected void connectSocket(
            final Socket sock,
            final InetSocketAddress remoteAddress,
            final Timeout connectTimeout,
            final HttpContext context)
            throws IOException {
        boolean success = false;
        long startNanos = System.nanoTime();
        try {
            super.connectSocket(sock, remoteAddress, connectTimeout, context);
            success = true;
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            Timer timer = connectInstrumentation.timer(success, context);
            timer.update(durationNanos, TimeUnit.NANOSECONDS);
        }
    }
}
