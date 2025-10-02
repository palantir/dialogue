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

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients.DeadlineEnforcementFactory;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.refreshable.Refreshable;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

public final class DeadlineEnforcementFactoryTest {

    private static final ServicesConfigBlock scb = ServicesConfigBlock.builder()
            .defaultSecurity(TestConfigurations.SSL_CONFIG)
            .putServices(
                    "test-service",
                    PartialServiceConfiguration.builder()
                            .addUris("https://127.0.0.1/test-service")
                            .build())
            .build();

    @Test
    void clients_with_different_deadline_enforcement_settings_have_different_channels() throws Exception {
        ReloadingFactory factoryDefault =
                DialogueClients.create(Refreshable.only(scb)).withUserAgent(TestConfigurations.AGENT);

        DeadlineEnforcementFactory factoryWithEnforcement = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(true);
        DeadlineEnforcementFactory factoryWithEnforcementDisabled = factoryDefault.withDeadlineEnforcement(false);

        Channel channelWithoutEnforcement = factoryDefault.getChannel("test-service");
        Channel channelWithEnforcement =
                getChannelFromDeadlineEnforcementFactory(factoryWithEnforcement, "test-service");
        Channel channelWithEnforcementDisabled =
                getChannelFromDeadlineEnforcementFactory(factoryWithEnforcementDisabled, "test-service");

        assertThat(channelWithoutEnforcement).isNotSameAs(channelWithEnforcement);
        assertThat(channelWithoutEnforcement).isNotSameAs(channelWithEnforcementDisabled);
        assertThat(channelWithEnforcement).isNotSameAs(channelWithEnforcementDisabled);
    }

    @Test
    void multiple_channels_from_same_factory_with_deadline_enforcement_work_correctly() throws Exception {
        ReloadingFactory factory =
                DialogueClients.create(Refreshable.only(scb)).withUserAgent(TestConfigurations.AGENT);
        DeadlineEnforcementFactory factoryWithEnforcement = factory.withDeadlineEnforcement(true);

        ChannelCache cache = getCacheFromFactory(factory);

        Channel channel1 = getChannelFromDeadlineEnforcementFactory(factoryWithEnforcement, "test-service");
        Channel channel2 = getChannelFromDeadlineEnforcementFactory(factoryWithEnforcement, "test-service");

        assertThat(channel1).isNotNull();
        assertThat(channel2).isNotNull();
        assertThat(getCacheSize(cache)).isEqualTo(1);
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

    private Channel getChannelFromDeadlineEnforcementFactory(DeadlineEnforcementFactory factory, String serviceName)
            throws Exception {
        Field delegateField = factory.getClass().getDeclaredField("delegate");
        delegateField.setAccessible(true);
        ReloadingFactory delegate = (ReloadingFactory) delegateField.get(factory);

        java.lang.reflect.Method getInternalDialogueChannelMethod =
                delegate.getClass().getDeclaredMethod("getInternalDialogueChannel", String.class);
        getInternalDialogueChannelMethod.setAccessible(true);
        Refreshable<?> refreshableChannel =
                (Refreshable<?>) getInternalDialogueChannelMethod.invoke(delegate, serviceName);
        Object internalChannel = refreshableChannel.get();

        Field enforceField = factory.getClass().getDeclaredField("enforce");
        enforceField.setAccessible(true);
        boolean enforce = (Boolean) enforceField.get(factory);

        return (endpoint, request) -> {
            request.attachments().put(com.palantir.dialogue.RequestAttachmentKey.create(Boolean.class), enforce);
            return ((Channel) internalChannel).execute(endpoint, request);
        };
    }
}
