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

import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;

final class DialogueHttpClientConnectionOperator extends DefaultHttpClientConnectionOperator {

    private static final String CONNECT_BEGAN_ATTRIBUTE = "onBeforeSocketConnectNanoTime";

    private final ConnectInstrumentation connectInstrumentation;

    DialogueHttpClientConnectionOperator(
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
    protected void onBeforeSocketConnect(HttpContext httpContext, HttpHost endpointHost) {
        super.onBeforeSocketConnect(httpContext, endpointHost);
        httpContext.setAttribute(CONNECT_BEGAN_ATTRIBUTE, System.nanoTime());
    }

    @Override
    protected void onAfterSocketConnect(HttpContext httpContext, HttpHost endpointHost) {
        super.onAfterSocketConnect(httpContext, endpointHost);
        Object value = httpContext.getAttribute(CONNECT_BEGAN_ATTRIBUTE);
        if (value instanceof Long) {
            long duration = System.nanoTime() - (long) value;
            connectInstrumentation.timer(true, httpContext).update(duration, TimeUnit.NANOSECONDS);
        }
    }
}
