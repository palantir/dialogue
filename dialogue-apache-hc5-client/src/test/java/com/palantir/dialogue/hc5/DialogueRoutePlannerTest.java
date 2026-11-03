/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.util.List;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class DialogueRoutePlannerTest {
    private static final ProxySelector PROXY =
            ProxySelector.of(InetSocketAddress.createUnresolved("proxy.example", 3128));

    @ParameterizedTest
    @CsvSource({
        "http, -1, 80, false, false",
        "http, 80, 80, false, false",
        "http, 8080, 8080, false, false",
        "https, -1, 443, false, true",
        "https, 443, 443, false, true",
        "https, 8443, 8443, false, true",
        "http, -1, 80, true, true",
        "http, 80, 80, true, true",
        "http, 8080, 8080, true, true",
        "https, -1, 443, true, true",
        "https, 443, 443, true, true",
        "https, 8443, 8443, true, true"
    })
    void tunnels_only_when_required(
            String scheme, int port, int expectedPort, boolean forceProxyTunnel, boolean tunnelled) throws Exception {
        HttpRoute route = new DialogueRoutePlanner(PROXY, forceProxyTunnel)
                .determineRoute(new HttpHost(scheme, "origin.example", port), HttpClientContext.create());

        assertThat(route.isTunnelled()).isEqualTo(tunnelled);
        assertThat(route.isSecure()).isEqualTo(scheme.equals("https"));
        assertThat(route.isLayered()).isEqualTo(scheme.equals("https"));
        assertThat(route.getProxyHost()).isEqualTo(new HttpHost("http", "proxy.example", 3128));
        assertThat(route.getTargetHost().getHostName()).isEqualTo("origin.example");
        assertThat(route.getTargetHost().getSchemeName()).isEqualTo(scheme);
        assertThat(route.getTargetHost().getPort()).isEqualTo(expectedPort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "https"})
    void leaves_direct_routes_untunnelled(String scheme) throws Exception {
        ProxySelector selector = mock(ProxySelector.class);
        when(selector.select(any())).thenReturn(List.of(Proxy.NO_PROXY));

        HttpRoute route = new DialogueRoutePlanner(selector, true)
                .determineRoute(new HttpHost(scheme, "origin.example", -1), HttpClientContext.create());

        assertThat(route.isTunnelled()).isFalse();
        assertThat(route.getProxyHost()).isNull();
        assertThat(route.isSecure()).isEqualTo(scheme.equals("https"));
    }

    @Test
    void preserves_tunnelling_with_a_pre_resolved_address() throws Exception {
        InetAddress address = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        HttpClientContext context = HttpClientContext.create();
        DialogueRoutePlanner.set(context, address);

        HttpRoute route = new DialogueRoutePlanner(PROXY, true)
                .determineRoute(new HttpHost("http", "origin.example", 8080), context);

        assertThat(route.isTunnelled()).isTrue();
        assertThat(route.isSecure()).isFalse();
        assertThat(route.isLayered()).isFalse();
        assertThat(route.getTargetHost()).isEqualTo(new HttpHost("http", address, "origin.example", 8080));
        assertThat(route.getProxyHost()).isEqualTo(new HttpHost("http", "proxy.example", 3128));
    }
}
