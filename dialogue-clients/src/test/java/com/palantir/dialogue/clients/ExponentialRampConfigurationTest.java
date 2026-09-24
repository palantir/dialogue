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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Gauge;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.ref.Reference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExponentialRampConfigurationTest {
    private static final String SERVICE = "service";
    private static final String URI = "https://127.0.0.1";
    private static final ServiceConfiguration SERVICE_CONFIG = ServiceConfiguration.builder()
            .security(TestConfigurations.SSL_CONFIG)
            .addUris(URI)
            .build();
    private static final ServicesConfigBlock SERVICES = ServicesConfigBlock.builder()
            .defaultSecurity(TestConfigurations.SSL_CONFIG)
            .putServices(
                    SERVICE, PartialServiceConfiguration.builder().addUris(URI).build())
            .build();

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void initial_limit_reaches_cached_and_direct_channels(boolean direct, boolean customLimit) {
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(SERVICES))
                .withUserAgent(TestConfigurations.AGENT)
                .withTaggedMetrics(metrics)
                .withDnsNodeDiscovery(false);
        if (customLimit) {
            factory = factory.withConcurrencyLimiterExponentialRamp(true, 50);
        }
        // The boolean-only overload must preserve a previously configured initial limit.
        factory = factory.withConcurrencyLimiterExponentialRamp(true);

        Channel channel = direct
                ? factory.getNonReloadingChannel(
                        SERVICE,
                        ClientConfiguration.builder()
                                .from(ClientConfigurations.of(SERVICE_CONFIG))
                                .taggedMetricRegistry(metrics)
                                .build())
                : factory.getChannel(SERVICE);

        try {
            assertThat(metrics.getMetrics().entrySet().stream()
                            .filter(entry -> entry.getKey().safeName().equals("dialogue.concurrencylimiter.max"))
                            .map(entry -> ((Gauge<?>) entry.getValue()).getValue()))
                    .singleElement()
                    .isEqualTo(customLimit ? 50D : 20D);
        } finally {
            // The gauge holds its limiter weakly, so retain the channel until after reading it.
            Reference.reachabilityFence(channel);
        }
    }

    @Test
    void different_initial_limits_do_not_share_cached_channels() {
        ChannelCache cache = ChannelCache.createEmptyCache();
        ImmutableReloadingParams params = ImmutableReloadingParams.builder()
                .scb(Refreshable.only(SERVICES))
                .userAgent(TestConfigurations.AGENT)
                .dnsNodeDiscovery(false)
                .concurrencyLimiterExponentialRamp(true)
                .build();
        ImmutableReloadingParams customParams = params.withConcurrencyLimiterExponentialRampInitialLimit(50);

        DialogueChannel defaultChannel = cache.getNonReloadingChannel(params, SERVICE_CONFIG, SERVICE);
        DialogueChannel customChannel = cache.getNonReloadingChannel(customParams, SERVICE_CONFIG, SERVICE);

        assertThat(customChannel).isNotSameAs(defaultChannel);
        assertThat(cache.getNonReloadingChannel(params, SERVICE_CONFIG, SERVICE))
                .isSameAs(defaultChannel);
        assertThat(cache.getNonReloadingChannel(customParams, SERVICE_CONFIG, SERVICE))
                .isSameAs(customChannel);
    }
}
