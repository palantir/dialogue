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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfigurationFactory;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.HostEventsSink;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.clients.ConjureClients;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.dialogue.clients.DialogueClients.StickyChannelFactory;
import com.palantir.dialogue.example.SampleServiceAsync;
import com.palantir.dialogue.example.SampleServiceBlocking;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.UnknownHostException;
import java.security.Provider;
import java.time.Duration;
import java.util.Optional;
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
                            .addUris("https://multipass.fake.palantir.com")
                            .build())
            .putServices(
                    "email-service",
                    PartialServiceConfiguration.builder()
                            .addUris("https://email-service.fake.palantir.com")
                            .build())
            .putServices(
                    "zero-uris-service", PartialServiceConfiguration.builder().build())
            .build();

    @Test
    void sensible_errors_if_service_does_not_exist_in_scb() {
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(Refreshable.only(scb)).withUserAgent(TestConfigurations.AGENT);

        SampleServiceBlocking unknown = factory.get(SampleServiceBlocking.class, "borf");
        assertThatThrownBy(unknown::voidToVoid)
                .hasMessageContaining("Service not configured (config block not present): "
                        + "{serviceName=borf, available=[multipass, email-service, zero-uris-service]}");
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

    @Test
    void getStickyChannels_behaves_when_service_doesnt_exist() {
        StickyChannelFactory stickyChannels = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0)
                .getStickyChannels("asldjaslkdjslad");

        ListenableFuture<Response> future = stickyChannels
                .getStickyChannel()
                .execute(TestEndpoint.POST, Request.builder().build());
        assertThatThrownBy(future::get)
                .describedAs("Nice error message when services doesn't exist")
                .hasCauseInstanceOf(SafeIllegalStateException.class)
                .hasMessageContaining("Service not configured");
    }

    @Test
    void getStickyChannels_behaves_when_just_one_uri() {
        StickyChannelFactory stickyChannels = DialogueClients.create(Refreshable.only(scb))
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0)
                .getStickyChannels("multipass");

        ListenableFuture<Response> future = stickyChannels
                .getStickyChannel()
                .execute(TestEndpoint.POST, Request.builder().build());
        assertThatThrownBy(future::get)
                .describedAs("Made a real network call")
                .hasCauseInstanceOf(UnknownHostException.class);
    }

    @Test
    void getStickyChannels_live_reloads_nicely() {
        SettableRefreshable<ServicesConfigBlock> refreshable = Refreshable.create(scb);
        StickyChannelFactory stickyChannels = DialogueClients.create(refreshable)
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0)
                .getStickyChannels("zero-uris-service");

        ListenableFuture<Response> future = stickyChannels
                .getStickyChannel()
                .execute(TestEndpoint.POST, Request.builder().build());
        assertThatThrownBy(future::get)
                .describedAs("Nice error message when service exists but has zero uris")
                .hasCauseInstanceOf(SafeIllegalStateException.class)
                .hasMessageContaining("Service not configured");

        refreshable.update(ServicesConfigBlock.builder()
                .from(scb)
                .putServices(
                        "zero-uris-service",
                        PartialServiceConfiguration.builder()
                                .addUris("https://live-reloaded-uri-appeared")
                                .build())
                .build());
        ListenableFuture<Response> future2 = stickyChannels
                .getStickyChannel()
                .execute(TestEndpoint.POST, Request.builder().build());
        assertThatThrownBy(future2::get)
                .describedAs("Made a real network call")
                .hasCauseInstanceOf(UnknownHostException.class);
    }

    @Test
    void perHost_attributes_concurrencylimiter_metrics_to_the_right_host() {
        DefaultTaggedMetricRegistry taggedMetrics = new DefaultTaggedMetricRegistry();
        DialogueClients.create(Refreshable.only(ServicesConfigBlock.builder()
                        .defaultSecurity(TestConfigurations.SSL_CONFIG)
                        .putServices(
                                "my-service",
                                PartialServiceConfiguration.builder()
                                        .addUris("https://my-service-0.fake.palantir.com")
                                        .addUris("https://my-service-1.fake.palantir.com")
                                        .addUris("https://my-service-2.fake.palantir.com")
                                        .build())
                        .build()))
                .withUserAgent(TestConfigurations.AGENT)
                .withMaxNumRetries(0)
                .withTaggedMetrics(taggedMetrics)
                .perHost("my-service");

        assertThat(taggedMetrics.getMetrics().keySet().stream()
                        .filter(metricName -> metricName.safeName().equals("dialogue.concurrencylimiter.max")))
                .describedAs("One concurrencylimiter metric per host")
                .hasSize(3);
    }

    @Test
    @SuppressWarnings("deprecation") // testing deprecated functionality
    void legacyClientConfigurationDoesntRequireUserAgent() {
        ClientConfiguration minimalConfiguration = ClientConfiguration.builder()
                .from(ClientConfigurations.of(
                        ServiceConfigurationFactory.of(scb).get("email-service")))
                .hostEventsSink(Optional.empty())
                .userAgent(Optional.empty())
                .build();
        ReloadingFactory factory = DialogueClients.create(
                        Refreshable.only(ServicesConfigBlock.builder().build()))
                .withUserAgent(TestConfigurations.AGENT);
        assertThatCode(() -> factory.getNonReloading(SampleServiceBlocking.class, minimalConfiguration))
                .doesNotThrowAnyException();
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
        public Alternative withHostEventsSink(HostEventsSink _hostEventsSink) {
            return this;
        }

        @Override
        public Alternative withNodeSelectionStrategy(NodeSelectionStrategy _strategy) {
            return this;
        }

        @Override
        public Alternative withFailedUrlCooldown(Duration _duration) {
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
