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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfigurationFactory;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines;
import com.palantir.deadlines.Deadlines.RequestDecodingAdapter;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.refreshable.Refreshable;
import com.palantir.tracing.CloseableTracer;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeadlineEnforcementTest {
    private Undertow undertow;
    private Handler undertowHandler;
    private ServicesConfigBlock scb;

    @BeforeEach
    void before() {
        SSLContext sslContext = SslSocketFactories.createSslContext(TestConfigurations.SSL_CONFIG);
        undertowHandler = new Handler();
        undertow = Undertow.builder()
                .addHttpsListener(
                        0,
                        "localhost",
                        sslContext,
                        new BlockingHandler(exchange -> undertowHandler.handleRequest(exchange)))
                .build();
        undertow.start();
        scb = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        "test-service",
                        PartialServiceConfiguration.builder()
                                .addUris(getUri(undertow))
                                .build())
                .build();
    }

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
    void client_throws_when_deadline_expired() {
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(true);

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "test-service");
        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            Map<String, String> inboundRequest = Map.of("Expect-Within", "0");
            Deadlines.parseFromRequest(
                    Optional.empty(), inboundRequest, DummyRequestDecoder.INSTANCE, Deadlines.Enforcement.DEFER);

            assertThatThrownBy(client::voidToVoid).isInstanceOf(DeadlineExpiredException.class);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void works_with_non_reloading_clients() {
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(true);

        ServiceConfiguration serviceConfig = ServiceConfigurationFactory.of(scb).get("test-service");

        SampleServiceBlocking client = factory.getNonReloading(SampleServiceBlocking.class, serviceConfig);
        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            Map<String, String> inboundRequest = Map.of("Expect-Within", "0");
            Deadlines.parseFromRequest(
                    Optional.empty(), inboundRequest, DummyRequestDecoder.INSTANCE, Deadlines.Enforcement.DEFER);

            assertThatThrownBy(client::voidToVoid).isInstanceOf(DeadlineExpiredException.class);
        }

        ClientConfiguration clientConfig = ClientConfigurations.of(serviceConfig);
        client = factory.getNonReloading(SampleServiceBlocking.class, clientConfig);
        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            Map<String, String> inboundRequest = Map.of("Expect-Within", "0");
            Deadlines.parseFromRequest(
                    Optional.empty(), inboundRequest, DummyRequestDecoder.INSTANCE, Deadlines.Enforcement.DEFER);

            assertThatThrownBy(client::voidToVoid).isInstanceOf(DeadlineExpiredException.class);
        }
    }

    @Test
    void client_does_not_throw_when_enforcement_disabled() {
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(false);

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "test-service");
        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            Map<String, String> inboundRequest = Map.of("Expect-Within", "0");
            Deadlines.parseFromRequest(
                    Optional.empty(), inboundRequest, DummyRequestDecoder.INSTANCE, Deadlines.Enforcement.DEFER);

            assertThatCode(client::voidToVoid).doesNotThrowAnyException();
            assertThat(undertowHandler.getReceivedEnforcementHeader()).hasValue("false");
        }
    }

    @Test
    void client_cannot_override_enforcement_when_already_disabled() {
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(true);

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "test-service");
        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            Map<String, String> inboundRequest = Map.of("Expect-Within", "0");
            // simulates a scenario where tracing state has already explicitly disabled enforcement
            // in this case, constructed dialogue clients cannot override the disabled state, even if enforcement
            // is explicitly requested
            Deadlines.parseFromRequest(
                    Optional.empty(), inboundRequest, DummyRequestDecoder.INSTANCE, Deadlines.Enforcement.DISABLE);

            assertThatCode(client::voidToVoid).doesNotThrowAnyException();
            assertThat(undertowHandler.getReceivedEnforcementHeader()).hasValue("false");
        }
    }

    @Test
    void client_can_disable_when_already_enforced() {
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(false);

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "test-service");
        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            Map<String, String> inboundRequest = Map.of("Expect-Within", "0");
            // simulates a scenario where tracing state has already explicitly enabled enforcement
            // in this case, constructed dialogue clients are still allowed to opt-out by disabling enforcement
            Deadlines.parseFromRequest(
                    Optional.empty(), inboundRequest, DummyRequestDecoder.INSTANCE, Deadlines.Enforcement.ENFORCE);

            assertThatCode(client::voidToVoid).doesNotThrowAnyException();
            assertThat(undertowHandler.getReceivedEnforcementHeader()).hasValue("false");
        }
    }

    @Test
    void client_propagates_enforcement_flag_when_enabled() {
        ReloadingFactory factory = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withDeadlineEnforcement(true);

        SampleServiceBlocking client = factory.get(SampleServiceBlocking.class, "test-service");
        try (CloseableTracer ignored = CloseableTracer.startSpan("test")) {
            Map<String, String> inboundRequest = Map.of("Expect-Within", "10.000");
            Deadlines.parseFromRequest(
                    Optional.empty(), inboundRequest, DummyRequestDecoder.INSTANCE, Deadlines.Enforcement.DEFER);

            assertThatCode(client::voidToVoid).doesNotThrowAnyException();
            assertThat(undertowHandler.getReceivedEnforcementHeader()).hasValue("true");
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

    private static final class Handler implements HttpHandler {
        private Optional<String> receivedEnforcementHeader;

        private synchronized void setReceivedEnforcementHeader(@Nullable String receivedEnforcementHeader) {
            this.receivedEnforcementHeader = Optional.ofNullable(receivedEnforcementHeader);
        }

        synchronized Optional<String> getReceivedEnforcementHeader() {
            return receivedEnforcementHeader;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            setReceivedEnforcementHeader(exchange.getRequestHeaders().getFirst("Expect-Within-Enforced"));
            exchange.setStatusCode(200);
        }
    }

    private static String getUri(Undertow undertow) {
        Undertow.ListenerInfo listenerInfo = Iterables.getOnlyElement(undertow.getListenerInfo());
        return String.format(
                "%s://localhost:%d",
                listenerInfo.getProtcol(), ((InetSocketAddress) listenerInfo.getAddress()).getPort());
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
