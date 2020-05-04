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

package com.palantir.dialogue.otherpackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.logsafe.Preconditions;
import com.palantir.refreshable.DefaultRefreshable;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.UnknownHostException;
import java.security.Provider;
import org.junit.jupiter.api.Test;

class DialogueClientsTest {

    DefaultTaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
    ServiceConfiguration serviceConf = ServiceConfiguration.builder()
            .security(TestConfigurations.SSL_CONFIG)
            .addUris("https://multipass")
            .build();

    ServicesConfigBlock scb = ServicesConfigBlock.builder()
            .defaultSecurity(TestConfigurations.SSL_CONFIG)
            .putServices(
                    "multipass",
                    PartialServiceConfiguration.builder()
                            .addUris("https://multipass")
                            .build())
            .putServices(
                    "email-service",
                    PartialServiceConfiguration.builder()
                            .addUris("https://email-service")
                            .build())
            .build();

    @Test
    void shorthand() {
        SampleServiceBlocking blocking = DialogueClients.create()
                .withUserAgent(TestConfigurations.AGENT)
                .getNonReloading(
                        SampleServiceBlocking.class,
                        ServiceConfiguration.builder()
                                .security(TestConfigurations.SSL_CONFIG)
                                .addUris("https://shorthand")
                                .maxNumRetries(0)
                                .build());
        assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("shorthand");
    }

    @Test
    void reloading() {
        ServicesConfigBlock oneService = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        "multipass",
                        PartialServiceConfiguration.builder()
                                .addUris("https://localhost")
                                .build())
                .build();
        DefaultRefreshable<ServicesConfigBlock> refreshable = new DefaultRefreshable<>(oneService);

        SampleServiceBlocking blocking = DialogueClients.create()
                .withUserAgent(TestConfigurations.AGENT)
                .reloading(refreshable)
                .get(SampleServiceBlocking.class, "multipass");
        assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("Connect to localhost");

        refreshable.update(ServicesConfigBlock.builder()
                .from(oneService)
                .putServices(
                        "multipass",
                        PartialServiceConfiguration.builder()
                                .addUris("https://other")
                                .build())
                .build());

        assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("other");
    }

    @Test
    void reloading_single() {
        DialogueClients.Factory factory = DialogueClients.create()
                .withTaggedMetrics(metrics)
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0);

        DialogueClients.SingleReloadingFactory fooFactory = factory.reloadingServiceConfiguration(
                        Refreshable.only(serviceConf))
                .withServiceName("foo");

        SampleServiceBlocking client = fooFactory.get(SampleServiceBlocking.class);

        assertThatThrownBy(client::voidToVoid).hasMessageContaining("multipass");
        assertThat(metrics.getMetrics().keySet().toString()).contains("dialogue-reloading-foo");

        SampleServiceBlocking client2 = factory.reloading(Refreshable.only(scb))
                .withServiceName("email-service")
                .get(SampleServiceBlocking.class);
        assertThatThrownBy(client2::voidToVoid).hasMessageContaining("email-service");
    }

    @Test
    void services_config_block() {
        Refreshable<ServicesConfigBlock> refreshable = Refreshable.only(scb);
        DialogueClients.ReloadingFactory facade =
                DialogueClients.create().reloading(refreshable).withUserAgent(TestConfigurations.AGENT);

        SampleServiceBlocking blocking = facade.withMaxNumRetries(0).get(SampleServiceBlocking.class, "multipass");
        assertThatThrownBy(blocking::voidToVoid)
                .hasCauseInstanceOf(UnknownHostException.class)
                .hasMessageContaining("multipass");

        SampleServiceBlocking blocking2 = facade.withMaxNumRetries(0).get(SampleServiceBlocking.class, "multipass");
        assertThatThrownBy(blocking2::voidToVoid)
                .hasCauseInstanceOf(UnknownHostException.class)
                .hasMessageContaining("multipass");

        SampleServiceBlocking blocking3 = facade.withMaxNumRetries(3).get(SampleServiceBlocking.class, "multipass");
        assertThatThrownBy(blocking3::voidToVoid)
                .hasCauseInstanceOf(UnknownHostException.class)
                .hasMessageContaining("multipass");

        SampleServiceBlocking unknown = facade.get(SampleServiceBlocking.class, "borf");
        assertThatThrownBy(unknown::voidToVoid)
                .hasMessageContaining("No service conf: {channelName=dialogue-reloading-borf}");
    }

    @Test
    void check_that_library_authors_can_depend_on_factory_interfaces() {
        DialogueClients.Factory factory = DialogueClients.create();

        // just want to see these things compile:
        simpleLibrary(factory.reloading(Refreshable.only(scb)));
        new LibraryClassWithMixins<>(factory).doSomething();
        libraryMethodWithMixins(factory);

        Alternative alternativeFactory = new Alternative();
        new LibraryClassWithMixins<>(alternativeFactory).doSomething();
    }

    // this is the recommended way to depend on a clientfactory
    private void simpleLibrary(DialogueClients.ReloadingFactory factory) {
        SampleServiceBlocking instance = factory.withMaxNumRetries(1)
                .withUserAgent(TestConfigurations.AGENT)
                .getNonReloading(SampleServiceBlocking.class, serviceConf);
        Preconditions.checkNotNull(instance, "instance");
    }

    // this made up library doesn't need live reloading, just a little bit of configurability
    private final class LibraryClassWithMixins<
            F extends DialogueClients.WithClientBehaviour<F> & DialogueClients.NonReloadingClientFactory> {

        private final SampleServiceBlocking client;

        private LibraryClassWithMixins(F factory) {
            client = factory.withUserAgent(TestConfigurations.AGENT)
                    .withClientQoS(ClientConfiguration.ClientQoS.DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS)
                    .withMaxNumRetries(0)
                    .getNonReloading(SampleServiceBlocking.class, serviceConf);
        }

        void doSomething() {
            try {
                client.voidToVoid();
            } catch (RuntimeException e) {
                // don't care
            }
        }
    }

    // sweet baby jesus what have i created. please never do this, just use 'Factory' or 'ReloadingFactory'
    <
                    T extends DialogueClients.WithClientBehaviour<T> & DialogueClients.ToReloadingFactory<U>,
                    U extends DialogueClients.ReloadingClientFactory>
            void libraryMethodWithMixins(T factory) {
        factory.withServerQoS(ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER)
                .reloading(Refreshable.only(scb))
                .get(SampleServiceAsync.class, "foo");
    }

    private static final class Alternative
            implements DialogueClients.NonReloadingClientFactory, DialogueClients.WithClientBehaviour<Alternative> {

        @Override
        public <T> T getNonReloading(Class<T> _clazz, ServiceConfiguration _serviceConf) {
            return null;
        }

        @Override
        public Alternative withTaggedMetrics(TaggedMetricRegistry _metrics) {
            return this;
        }

        @Override
        public Alternative withUserAgent(UserAgent _agent) {
            return this;
        }

        @Override
        public Alternative withNodeSelectionStrategy(NodeSelectionStrategy _strategy) {
            return this;
        }

        @Override
        public Alternative withClientQoS(ClientConfiguration.ClientQoS _value) {
            return this;
        }

        @Override
        public Alternative withServerQoS(ClientConfiguration.ServerQoS _value) {
            return this;
        }

        @Override
        public Alternative withRetryOnTimeout(ClientConfiguration.RetryOnTimeout _value) {
            return this;
        }

        @Override
        public Alternative withSecurityProvider(Provider _securityProvider) {
            return this;
        }

        @Override
        public Alternative withMaxNumRetries(int _maxNumRetries) {
            return this;
        }
    }
}
