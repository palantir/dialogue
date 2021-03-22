/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.myservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.ByteStreams;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.UndertowServer;
import com.palantir.dialogue.clients.DialogueClients;
import com.palantir.refreshable.Refreshable;
import io.undertow.util.HttpString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class MyServiceTest {

    @RegisterExtension
    public final UndertowServer undertow = new UndertowServer();

    private MyService myServiceDialogue;

    @BeforeEach
    public void beforeEach() {
        PartialServiceConfiguration partialServiceConfiguration = PartialServiceConfiguration.builder()
                .addUris(undertow.getUri() + "/my-service-dialogue")
                .build();
        ServicesConfigBlock scb = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices("myServiceDialogue", partialServiceConfiguration)
                .build();
        DialogueClients.ReloadingFactory factory =
                DialogueClients.create(Refreshable.create(scb)).withUserAgent(TestConfigurations.AGENT);
        myServiceDialogue = factory.get(MyService.class, "myServiceDialogue");
    }

    @Test
    public void testGreet() {
        undertow.setHandler(exchange -> {
            exchange.getResponseHeaders().add(HttpString.tryFromString("Content-Type"), "application/json");
            exchange.setStatusCode(200);
            ByteStreams.copy(exchange.getInputStream(), exchange.getOutputStream());
        });

        assertThat(myServiceDialogue.greet("Hello")).isEqualTo("Hello");
    }
}
