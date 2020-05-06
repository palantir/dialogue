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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.clients.ConjureClients;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.logsafe.Preconditions;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.security.Provider;
import org.junit.jupiter.api.Test;

class DialogueClientsTest {

    private static final ServiceConfiguration serviceConf = ServiceConfiguration.builder()
            .security(TestConfigurations.SSL_CONFIG)
            .addUris("https://multipass")
            .build();

    private static final ServicesConfigBlock scb = ServicesConfigBlock.builder()
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
    void sensible_errors_if_service_does_not_exist_in_scb() {
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(Refreshable.only(scb)).withUserAgent(TestConfigurations.AGENT);

        SampleServiceBlocking unknown = factory.get(SampleServiceBlocking.class, "borf");
        assertThatThrownBy(unknown::voidToVoid)
                .hasMessageContaining("Service not configured (config block not present): "
                        + "{serviceName=borf, available=[multipass, email-service]}");
    }

    @Test
    void check_that_library_authors_can_depend_on_factory_interfaces() {
        DialogueClients.ReloadingFactory factory = DialogueClients.create(Refreshable.only(scb));

        // just want to see these things compile:
        simpleLibrary(factory);
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
    private static final class LibraryClassWithMixins<
            F extends ConjureClients.WithClientOptions<F> & ConjureClients.NonReloadingClientFactory> {

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

    // sweet baby jesus what have i created. please never do this, just use 'ReloadingFactory'
    <
                    T extends ConjureClients.WithClientOptions<T> & ConjureClients.ToReloadingFactory<U>,
                    U extends ConjureClients.ReloadingClientFactory>
            void libraryMethodWithMixins(T factory) {
        factory.withServerQoS(ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER)
                .reloading(Refreshable.only(scb))
                .get(SampleServiceAsync.class, "foo");
    }

    private static final class Alternative
            implements ConjureClients.NonReloadingClientFactory, ConjureClients.WithClientOptions<Alternative> {

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
