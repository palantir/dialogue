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

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.example.SampleServiceBlocking;
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
        SampleServiceBlocking blocking = Facade.create().get(SampleServiceBlocking.class, localhost);
        assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("Connect to localhost");
    }

    @Test
    void reloading() {
        when(mockSupplier.get()).thenReturn(localhost).thenReturn(other);

        SampleServiceBlocking blocking = Facade.create().get(SampleServiceBlocking.class, mockSupplier);
        assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("Connect to localhost");

        Awaitility.await("Polling should eventually notice the reloaded config")
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThatThrownBy(blocking::voidToVoid).hasMessageContaining("other");
                });
    }
}
