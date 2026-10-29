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

import com.palantir.conjure.java.client.config.HttpsProxies;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

final class HttpsProxyDefaultRoutePlannerTest {

    private static final InetSocketAddress PROXY_ADDRESS =
            InetSocketAddress.createUnresolved("proxy.example.com", 3128);

    @Test
    void honorsSelectorAnswerChanges() throws HttpException {
        List<List<Proxy>> answers = List.of(
                List.of(Proxy.NO_PROXY),
                List.of(new Proxy(Proxy.Type.HTTP, PROXY_ADDRESS)),
                List.of(HttpsProxies.create(PROXY_ADDRESS)),
                List.of(new Proxy(Proxy.Type.SOCKS, PROXY_ADDRESS)),
                List.of(new Proxy(Proxy.Type.SOCKS, PROXY_ADDRESS), new Proxy(Proxy.Type.HTTP, PROXY_ADDRESS)));
        int[] calls = {0};
        HttpsProxyDefaultRoutePlanner planner =
                new HttpsProxyDefaultRoutePlanner(new TestProxySelector(_uri -> answers.get(calls[0]++)));
        HttpHost target = new HttpHost("https", "some-host.example.com", 8443);

        assertThat(determineProxy(planner, target)).isNull();
        assertThat(determineProxy(planner, target)).isEqualTo(new HttpHost("http", "proxy.example.com", 3128));
        assertThat(determineProxy(planner, target)).isEqualTo(new HttpHost("https", "proxy.example.com", 3128));
        assertThat(determineProxy(planner, target)).isNull();
        assertThat(determineProxy(planner, target)).isEqualTo(new HttpHost("http", "proxy.example.com", 3128));
        assertThat(calls[0]).isEqualTo(answers.size());
    }

    @Test
    void differentTargetsDoNotShareAnswers() throws HttpException {
        List<HttpHost> targets = List.of(
                new HttpHost("https", "a.example.com", 443),
                new HttpHost("https", "b.example.com", 443),
                new HttpHost("https", "a.example.com", 8443),
                new HttpHost("http", "a.example.com", 443),
                new HttpHost("https", "::1", 443),
                new HttpHost("https", "under_score.example.com", 443));
        // Each target is proxied through a port matching its index, so a shared answer shows up as the wrong port
        TestProxySelector selector = new TestProxySelector(uri -> {
            int index = targets.stream().map(HttpHost::toURI).toList().indexOf(uri.toString());
            return List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy", 1000 + index)));
        });
        HttpsProxyDefaultRoutePlanner planner = new HttpsProxyDefaultRoutePlanner(selector);

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < targets.size(); i++) {
                assertThat(determineProxy(planner, targets.get(i))).isEqualTo(new HttpHost("http", "proxy", 1000 + i));
            }
        }
        assertThat(selector.uris).hasSize(3 * targets.size());
    }

    @Test
    void preservesHostNameCase() throws Exception {
        TestProxySelector selector = new TestProxySelector(_uri -> List.of(Proxy.NO_PROXY));
        HttpsProxyDefaultRoutePlanner planner = new HttpsProxyDefaultRoutePlanner(selector);
        HttpHost upper = new HttpHost("https", "Some-Host.example.com", 443);
        HttpHost lower = new HttpHost("https", "some-host.example.com", 443);
        assertThat(upper).isEqualTo(lower);

        determineProxy(planner, upper);
        determineProxy(planner, lower);
        determineProxy(planner, upper);

        assertThat(selector.uris)
                .containsExactly(new URI(upper.toURI()), new URI(lower.toURI()), new URI(upper.toURI()))
                .extracting(URI::getHost)
                .containsExactly("Some-Host.example.com", "some-host.example.com", "Some-Host.example.com");
    }

    @Test
    void reusesTargetUri() throws HttpException {
        TestProxySelector selector = new TestProxySelector(_uri -> List.of(Proxy.NO_PROXY));
        HttpsProxyDefaultRoutePlanner planner = new HttpsProxyDefaultRoutePlanner(selector);
        HttpHost target = new HttpHost("https", "some-host.example.com", 443);

        determineProxy(planner, target);
        determineProxy(planner, new HttpHost("https", "some-host.example.com", 443));

        assertThat(selector.uris).hasSize(2);
        assertThat(selector.uris.get(1)).isSameAs(selector.uris.get(0));
    }

    @Test
    void manyTargets() throws Exception {
        TestProxySelector selector = new TestProxySelector(_uri -> List.of(Proxy.NO_PROXY));
        HttpsProxyDefaultRoutePlanner planner = new HttpsProxyDefaultRoutePlanner(selector);
        List<URI> expected = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 1000; i++) {
                HttpHost target = new HttpHost("https", "host-" + i + ".example.com", 443);
                assertThat(determineProxy(planner, target)).isNull();
                expected.add(new URI(target.toURI()));
            }
        }
        assertThat(selector.uris).containsExactlyElementsOf(expected);
    }

    @Test
    @SuppressWarnings("NullAway") // exercising the null selector fallback
    void nullSelectorUsesDefault() throws HttpException {
        HttpHost target = new HttpHost("https", "some-host.example.com", 443);
        assertThat(determineProxy(new HttpsProxyDefaultRoutePlanner(null), target))
                .isEqualTo(determineProxy(new HttpsProxyDefaultRoutePlanner(ProxySelector.getDefault()), target));
    }

    private static HttpHost determineProxy(HttpsProxyDefaultRoutePlanner planner, HttpHost target)
            throws HttpException {
        return planner.determineProxy(target, HttpClientContext.create());
    }

    private static final class TestProxySelector extends ProxySelector {
        private final Function<URI, List<Proxy>> answer;
        private final List<URI> uris = new ArrayList<>();

        TestProxySelector(Function<URI, List<Proxy>> answer) {
            this.answer = answer;
        }

        @Override
        public List<Proxy> select(URI uri) {
            uris.add(uri);
            return answer.apply(uri);
        }

        @Override
        public void connectFailed(URI _uri, SocketAddress _sa, IOException _ioe) {}
    }
}
