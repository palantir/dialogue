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

import com.palantir.conjure.java.client.config.HttpsProxy;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import javax.annotation.CheckForNull;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Identical to {@link SystemDefaultRoutePlanner} but adds support for connecting to an HTTPS proxy.
 */
public final class HttpsProxyDefaultRoutePlanner extends DefaultRoutePlanner {
    private final ProxySelector proxySelector;

    public HttpsProxyDefaultRoutePlanner(ProxySelector proxySelector) {
        super(null);
        this.proxySelector = proxySelector;
    }

    @Override
    @CheckForNull
    public HttpHost determineProxy(final HttpHost target, final HttpContext _context) throws HttpException {
        final URI targetUri;
        try {
            targetUri = new URI(target.toURI());
        } catch (final URISyntaxException ex) {
            throw new HttpException("Cannot convert host to URI: " + target, ex);
        }
        ProxySelector proxySelectorInstance = this.proxySelector;
        if (proxySelectorInstance == null) {
            proxySelectorInstance = ProxySelector.getDefault();
        }
        if (proxySelectorInstance == null) {
            // The proxy selector can be "unset", so we must be able to deal with a null selector
            return null;
        }
        final List<Proxy> proxies = proxySelectorInstance.select(targetUri);
        final Proxy p = chooseProxy(proxies);
        HttpHost result = null;
        if (p.type() == Proxy.Type.HTTP) {
            // convert the socket address to an HttpHost
            if (!(p.address() instanceof InetSocketAddress)) {
                throw new HttpException("Unable to handle non-Inet proxy address: " + p.address());
            }
            final InetSocketAddress isa = (InetSocketAddress) p.address();
            String scheme = p instanceof HttpsProxy ? "https" : "http";
            result = new HttpHost(scheme, isa.getAddress(), isa.getHostString(), isa.getPort());
        }

        return result;
    }

    private Proxy chooseProxy(final List<Proxy> proxies) {
        Proxy result = null;
        // check the list for one we can use
        for (int i = 0; (result == null) && (i < proxies.size()); i++) {
            final Proxy p = proxies.get(i);
            switch (p.type()) {
                case DIRECT:
                case HTTP:
                    result = p;
                    break;

                case SOCKS:
                    // SOCKS hosts are not handled on the route level.
                    // The socket may make use of the SOCKS host though.
                    break;
            }
        }
        if (result == null) {
            // @@@ log as warning or info that only a socks proxy is available?
            // result can only be null if all proxies are socks proxies
            // socks proxies are not handled on the route planning level
            result = Proxy.NO_PROXY;
        }
        return result;
    }
}
