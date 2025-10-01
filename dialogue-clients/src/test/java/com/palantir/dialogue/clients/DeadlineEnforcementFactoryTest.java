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

import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.clients.DialogueClients.DeadlineEnforcementFactory;
import com.palantir.dialogue.clients.DialogueClients.ReloadingFactory;
import com.palantir.refreshable.Refreshable;
import org.junit.jupiter.api.Test;

public final class DeadlineEnforcementFactoryTest {

    @Test
    public void testDeadlineEnforcementFactoryCreation() {
        ServicesConfigBlock servicesConfig = ServicesConfigBlock.builder().build();
        ReloadingFactory dialogueFactory = DialogueClients.create(Refreshable.only(servicesConfig));

        DeadlineEnforcementFactory enforcementFactory = dialogueFactory.withDeadlineEnforcement("test-service", true);

        assertThat(enforcementFactory).isNotNull();
    }

    @Test
    public void testDifferentEnforcementStrategies() {
        ServicesConfigBlock servicesConfig = ServicesConfigBlock.builder().build();
        ReloadingFactory dialogueFactory = DialogueClients.create(Refreshable.only(servicesConfig));

        DeadlineEnforcementFactory withDeadlines = dialogueFactory.withDeadlineEnforcement("test-service", true);
        DeadlineEnforcementFactory withoutDeadlines = dialogueFactory.withDeadlineEnforcement("test-service", false);

        assertThat(withDeadlines).isNotEqualTo(withoutDeadlines);
        //assertThat(withDeadlines.get(...)).isNotEqualTo(withDeadlines.get(...));
    }
}