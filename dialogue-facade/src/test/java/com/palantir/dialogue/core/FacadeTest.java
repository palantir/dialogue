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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.example.SampleServiceBlocking;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FacadeTest {
    ClientConfiguration localhost = TestConfigurations.create("https://localhost:8080");
    ClientConfiguration other = TestConfigurations.create("https://other:8080");

    @Mock
    Supplier<ClientConfiguration> mockSupplier;

    @Test
    void one_off() {
        SampleServiceBlocking blocking =
                Facade.create().withUserAgent(TestConfigurations.AGENT).get(SampleServiceBlocking.class, localhost);
        assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("Connect to localhost");
    }

    @Test
    void shorthand() {
        SampleServiceBlocking blocking = Facade.create()
                .withUserAgent(TestConfigurations.AGENT)
                .get(
                        SampleServiceBlocking.class,
                        ServiceConfiguration.builder()
                                .security(TestConfigurations.SSL_CONFIG)
                                .addUris("https://shorthand")
                                .build());
        assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("shorthand");
    }

    @Test
    void reloading() {
        when(mockSupplier.get()).thenReturn(localhost).thenReturn(other);

        SampleServiceBlocking blocking =
                Facade.create().withUserAgent(TestConfigurations.AGENT).get(SampleServiceBlocking.class, mockSupplier);
        assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("Connect to localhost");

        Awaitility.await("Polling should eventually notice the reloaded config")
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("other");
                });
    }

    @Test
    void facade2() {
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
        ScbFacade facade = Facade.create().withServiceConfigBlock(() -> scb).withUserAgent(TestConfigurations.AGENT);

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

        SampleServiceBlocking unknown = facade.get(SampleServiceBlocking.class, "unknown");
        assertThatThrownBy(unknown::voidToVoid).hasMessageContaining("Service not configured");
    }
}
