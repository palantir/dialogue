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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Timeout;

final class InstrumentedHttpClientConnectionOperator extends DefaultHttpClientConnectionOperator {

    private static final String CONNECT_BEGAN_ATTRIBUTE = "onBeforeSocketConnectNanoTime";

    private final ConnectInstrumentation connectInstrumentation;

    InstrumentedHttpClientConnectionOperator(
            DetachedSocketFactory detachedSocketFactory,
            DnsResolver dnsResolver,
            TlsSocketStrategy tlsSocketStrategy,
            ConnectInstrumentation connectInstrumentation) {
        super(
                detachedSocketFactory,
                null,
                dnsResolver,
                RegistryBuilder.<TlsSocketStrategy>create()
                        .register(URIScheme.HTTPS.id, tlsSocketStrategy)
                        .build());
        this.connectInstrumentation = connectInstrumentation;
    }

    @Override
    public void connect(
            ManagedHttpClientConnection conn,
            HttpHost endpointHost,
            NamedEndpoint endpointName,
            Path unixDomainSocket,
            InetSocketAddress localAddress,
            Timeout connectTimeout,
            SocketConfig socketConfig,
            Object attachment,
            HttpContext context)
            throws IOException {
        try {
            super.connect(
                    conn,
                    endpointHost,
                    endpointName,
                    unixDomainSocket,
                    localAddress,
                    connectTimeout,
                    socketConfig,
                    attachment,
                    context);
        } catch (IOException e) {
            recordPendingConnect(context, false);
            throw e;
        }
    }

    @Override
    protected void onBeforeSocketConnect(HttpContext httpContext, HttpHost endpointHost) {
        super.onBeforeSocketConnect(httpContext, endpointHost);
        // A pending start time means the previous address of a multi-address host failed to connect.
        recordPendingConnect(httpContext, false);
        httpContext.setAttribute(CONNECT_BEGAN_ATTRIBUTE, System.nanoTime());
    }

    @Override
    protected void onAfterSocketConnect(HttpContext httpContext, HttpHost endpointHost) {
        recordPendingConnect(httpContext, true);
        super.onAfterSocketConnect(httpContext, endpointHost);
    }

    private void recordPendingConnect(HttpContext httpContext, boolean success) {
        if (httpContext.removeAttribute(CONNECT_BEGAN_ATTRIBUTE) instanceof Long startNanos) {
            long duration = System.nanoTime() - startNanos;
            connectInstrumentation.timer(success, httpContext).update(duration, TimeUnit.NANOSECONDS);
        }
    }
}
