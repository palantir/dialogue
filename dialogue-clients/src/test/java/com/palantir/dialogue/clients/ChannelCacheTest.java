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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.dialogue.hc5.ApacheHttpClientChannels;
import com.palantir.logsafe.exceptions.SafeUnsupportedOperationException;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import java.net.InetAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChannelCacheTest {

    private final ChannelCache cache = ChannelCache.createEmptyCache();
    private final ServiceConfiguration serviceConf = ServiceConfiguration.builder()
            .security(TestConfigurations.SSL_CONFIG)
            .build();

    private Undertow undertow;
    private String uri;
    private HttpHandler undertowHandler;

    @BeforeEach
    public void before() {
        undertow = Undertow.builder()
                .addHttpListener(
                        0, "localhost", new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();

        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        uri = String.format("%s:/%s", listenerInfo.getProtcol(), listenerInfo.getAddress());
    }

    @AfterEach
    public void after() {
        undertow.stop();
    }

    @Test
    void identical_requests_are_hits() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        assertThat(cacheResult).isSameAs(cacheResult2);
    }

    @Test
    void different_channel_name_is_miss() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName2")
                .build());

        assertThat(cacheResult).isNotSameAs(cacheResult2);
        assertThat(cache.toString()).contains("apacheCache.size=2");
    }

    @Test
    void different_dns_resolver_new_instance() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(_hostname -> ImmutableSet.of())
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        assertThat(cacheResult).isNotSameAs(cacheResult2);
    }

    @Test
    void new_config_evicts_client_but_old_one_is_still_usable() {
        ChannelCache.ApacheCacheEntry cacheResult = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(serviceConf)
                .channelName("channelName")
                .build());

        ChannelCache.ApacheCacheEntry cacheResult2 = cache.getApacheClient(ImmutableApacheClientRequest.builder()
                .dnsResolver(StubDnsResolver.INSTANCE)
                .serviceConf(ServiceConfiguration.builder()
                        .from(serviceConf)
                        .enableHttp2(false)
                        .build())
                .channelName("channelName")
                .build());

        assertThat(cacheResult).isNotSameAs(cacheResult2);
        assertThat(cache.toString()).contains("apacheCache.size=1");

        undertowHandler = exchange -> {
            exchange.setStatusCode(200);
        };

        // Some clients might still be using this channel even though we're evicting it from the cache, so it's
        // important that the evicted client is still usable. Otherwise, we get support tickets like PDS-118523
        // where outgoing requests fail with 'Connection pool shut down'
        SampleServiceBlocking evictedClient = sampleServiceBlocking(cacheResult.client());
        evictedClient.voidToVoid();

        SampleServiceBlocking client2 = sampleServiceBlocking(cacheResult2.client());
        client2.voidToVoid();
    }

    private SampleServiceBlocking sampleServiceBlocking(ApacheHttpClientChannels.CloseableClient apache) {
        return SampleServiceBlocking.of(
                ApacheHttpClientChannels.createSingleUri(uri, apache),
                DefaultConjureRuntime.builder().build());
    }

    private enum StubDnsResolver implements DialogueDnsResolver {
        INSTANCE;

        @Override
        public ImmutableSet<InetAddress> resolve(String _hostname) {
            throw new SafeUnsupportedOperationException();
        }
    }
}
