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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.DialogueService;
import com.palantir.dialogue.DialogueServiceFactory;
import com.palantir.dialogue.EndpointChannelFactory;
import org.junit.jupiter.api.Test;

class ReflectionTest {
    private static final ConjureRuntime RUNTIME =
            DefaultConjureRuntime.builder().build();

    @Test
    void testAnnotationIsPreferred() {
        Channel channel = mock(Channel.class);
        Client0 client = Reflection.callStaticFactoryMethod(Client0.class, channel, RUNTIME);
        assertThat(client).isEqualTo(Client0Impls.THIRD);
    }

    @Test
    void testChannelFactoryUsedOverEndpointFactory() {
        // No real reason for this decision other than historical context, no reason to move to an intermediate
        // factory when the annotation model is preferred.
        Channel channel = mock(Channel.class);
        Client1 client = Reflection.callStaticFactoryMethod(Client1.class, channel, RUNTIME);
        assertThat(client).isEqualTo(Client1Impls.SECOND);
    }

    @Test
    void testStaticEndpointChannelFactoryIsUsed() {
        Channel channel = mock(Channel.class);
        Client2 client = Reflection.callStaticFactoryMethod(Client2.class, channel, RUNTIME);
        assertThat(client).isNotNull();
    }

    @DialogueService(Client0Factory.class)
    interface Client0 {

        static Client0 of(EndpointChannelFactory _endpointChannelFactory, ConjureRuntime _runtime) {
            return Client0Impls.FIRST;
        }

        static Client0 of(Channel _channel, ConjureRuntime _runtime) {
            return Client0Impls.SECOND;
        }
    }

    public static final class Client0Factory implements DialogueServiceFactory<Client0> {

        @Override
        public Client0 create(EndpointChannelFactory _endpointChannelFactory, ConjureRuntime _runtime) {
            return Client0Impls.THIRD;
        }
    }

    enum Client0Impls implements Client0 {
        FIRST,
        SECOND,
        THIRD;
    }

    interface Client1 {

        static Client1 of(EndpointChannelFactory _endpointChannelFactory, ConjureRuntime _runtime) {
            return Client1Impls.FIRST;
        }

        static Client1 of(Channel _channel, ConjureRuntime _runtime) {
            return Client1Impls.SECOND;
        }
    }

    enum Client1Impls implements Client1 {
        FIRST,
        SECOND;
    }

    interface Client2 {

        static Client2 of(EndpointChannelFactory _endpointChannelFactory, ConjureRuntime _runtime) {
            return new Client2() {};
        }
    }
}
