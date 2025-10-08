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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines;
import com.palantir.deadlines.Deadlines.RequestDecodingAdapter;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import com.palantir.tracing.CloseableTracer;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DeadlineEnforcementTest {

    private static final ServicesConfigBlock scb = ServicesConfigBlock.builder()
            .defaultSecurity(TestConfigurations.SSL_CONFIG)
            .putServices(
                    "test-service",
                    PartialServiceConfiguration.builder()
                            .addUris("https://127.0.0.1/test-service")
                            .build())
            .build();

    @Test
    void clients_with_different_deadline_enforcement_settings_have_different_channels() {
        ReloadingFactory factoryDefault =
                DialogueClients.create(Refreshable.only(scb)).withUserAgent(TestConfigurations.AGENT);

        ReloadingFactory factoryWithEnforcement = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(true);
        ReloadingFactory factoryWithEnforcementDisabled = factoryDefault.withDeadlineEnforcement(false);

        Channel channelWithoutEnforcement = factoryDefault.getChannel("test-service");
        Channel channelWithEnforcement = factoryWithEnforcement.getChannel("test-service");
        Channel channelWithEnforcementDisabled = factoryWithEnforcementDisabled.getChannel("test-service");

        assertThat(channelWithoutEnforcement).isNotSameAs(channelWithEnforcement);
        assertThat(channelWithoutEnforcement).isNotSameAs(channelWithEnforcementDisabled);
    }

    @Test
    void multiple_channels_from_same_factory_with_deadline_enforcement_work_correctly() throws Exception {
        ReloadingFactory factoryWithEnforcement = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(true);

        ChannelCache cache = getCacheFromFactory(factoryWithEnforcement);

        Channel channel1 = factoryWithEnforcement.getChannel("test-service");
        Channel channel2 = factoryWithEnforcement.getChannel("test-service");

        assertThat(channel1).isNotNull();
        assertThat(channel2).isNotNull();
        assertThat(getCacheSize(cache)).isEqualTo(1);
    }

    @Test
    void client_throws_when_deadline_expired() throws Exception {
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(true);

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "test-service");
        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            Map<String, String> inboundRequest = Map.of("Expect-Within", "0");
            Deadlines.parseFromRequest(
                    Optional.empty(), inboundRequest, DummyRequestDecoder.INSTANCE, Deadlines.Enforcement.DEFER);

            assertThatThrownBy(() -> {
                        client.voidToVoid();
                    })
                    .isInstanceOf(DeadlineExpiredException.class);
        }
    }

    private enum DummyRequestDecoder implements RequestDecodingAdapter<Map<String, String>> {
        INSTANCE;

        @Override
        public Optional<String> getFirstHeader(Map<String, String> _headers, String _headerName) {
            throw new IllegalStateException("not implemented");
        }

        @Override
        public @Nullable String maybeFirstHeader(Map<String, String> headers, String headerName) {
            return headers.get(headerName);
        }
    }

    // Reflection hackery below
    private ChannelCache getCacheFromFactory(ReloadingFactory factory) throws Exception {
        Field cacheField = factory.getClass().getDeclaredField("cache");
        cacheField.setAccessible(true);
        return (ChannelCache) cacheField.get(factory);
    }

    private long getCacheSize(ChannelCache cache) throws Exception {
        Field channelCacheField = cache.getClass().getDeclaredField("channelCache");
        channelCacheField.setAccessible(true);
        LoadingCache<?, ?> channelCache = (LoadingCache<?, ?>) channelCacheField.get(cache);
        return channelCache.estimatedSize();
    }
}
