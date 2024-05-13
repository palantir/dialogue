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

import java.net.InetAddress;
import java.net.ProxySelector;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * This implementation wraps the default route planner, but allows a specific pre-resolved address to be used.
 * Client instances are shared between all URIs, so this route planner cannot know resolved addresses at the
 * moment it's created, and instead must rely on callers passing the resolved address using the
 * {@link HttpContext}.
 */
final class DialogueRoutePlanner implements HttpRoutePlanner {
    private static final String ATTRIBUTE = "dialogueResolvedAddress";
    private final HttpRoutePlanner delegate;

    DialogueRoutePlanner(ProxySelector proxySelector) {
        delegate = new HttpsProxyDefaultRoutePlanner(proxySelector);
    }

    @Override
    public HttpRoute determineRoute(HttpHost host, HttpContext context) throws HttpException {
        HttpRoute route = delegate.determineRoute(host, context);
        if (route.getTargetHost().getAddress() == null) {
            InetAddress resolvedAddress = get(context);
            if (resolvedAddress != null) {
                return withResolvedAddress(route, resolvedAddress);
            }
        }
        return route;
    }

    private static HttpRoute withResolvedAddress(HttpRoute route, InetAddress resolvedAddress) {
        HttpHost targetHost = route.getTargetHost();
        return new HttpRoute(
                new HttpHost(
                        targetHost.getSchemeName(), resolvedAddress, targetHost.getHostName(), targetHost.getPort()),
                route.getLocalAddress(),
                // We don't really expect proxies to be used with pre-resolved addresses, however
                // that takes place at a different layer of the implementation.
                extractProxies(route),
                route.isSecure(),
                route.getTunnelType(),
                route.getLayerType());
    }

    @Nullable
    private static HttpHost[] extractProxies(HttpRoute route) {
        int hops = route.getHopCount();
        if (hops > 1) {
            HttpHost[] proxies = new HttpHost[hops - 1];
            for (int i = 0; i < hops - 1; i++) {
                proxies[i] = route.getHopTarget(i);
            }
            return proxies;
        }
        return null;
    }

    static void set(HttpContext context, InetAddress resolvedAddress) {
        context.setAttribute(ATTRIBUTE, resolvedAddress);
    }

    static boolean hasPreResolvedAddress(HttpContext context) {
        return context != null && get(context) != null;
    }

    @Nullable
    private static InetAddress get(HttpContext context) {
        Object value = context.getAttribute(ATTRIBUTE);
        if (value instanceof InetAddress) {
            return (InetAddress) value;
        }
        return null;
    }
}
