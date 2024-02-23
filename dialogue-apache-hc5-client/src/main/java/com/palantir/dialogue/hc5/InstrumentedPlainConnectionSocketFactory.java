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

import com.codahale.metrics.Timer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

final class InstrumentedPlainConnectionSocketFactory extends PlainConnectionSocketFactory {

    private final Supplier<Socket> simpleSocketCreator;
    private final ConnectInstrumentation connectInstrumentation;

    InstrumentedPlainConnectionSocketFactory(
            Supplier<Socket> simpleSocketCreator, ConnectInstrumentation connectInstrumentation) {
        this.simpleSocketCreator = simpleSocketCreator;
        this.connectInstrumentation = connectInstrumentation;
    }

    @Override
    public Socket connectSocket(
            TimeValue connectTimeout,
            Socket socket,
            HttpHost host,
            InetSocketAddress remoteAddress,
            InetSocketAddress localAddress,
            HttpContext context)
            throws IOException {
        boolean success = false;
        long startNanos = System.nanoTime();
        try {
            Socket result = super.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
            success = true;
            return result;
        } finally {
            long durationNanos = System.nanoTime() - startNanos;
            Timer timer = connectInstrumentation.timer(success, context);
            timer.update(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public Socket createSocket(HttpContext _context) {
        return simpleSocketCreator.get();
    }
}
