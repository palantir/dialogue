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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultNodeSelectionStrategySelectorTest {

    @Mock
    LimitedChannel channelA;

    @Mock
    LimitedChannel channelB;

    private DefaultNodeSelectionStrategySelector strategySelector;

    @BeforeEach
    void beforeEach() {
        strategySelector = new DefaultNodeSelectionStrategySelector(NodeSelectionStrategy.PIN_UNTIL_ERROR);
    }

    @Test
    void defaults_to_client_provided_strategy() {
        assertThat(strategySelector.getCurrentStrategy()).isEqualTo(DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR);
    }

    @Test
    void uses_server_provided_strategy() {
        DialogueNodeSelectionStrategy strategy = strategySelector.updateChannelStrategy(
                channelA, ImmutableList.of(DialogueNodeSelectionStrategy.BALANCED));
        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.BALANCED);
    }

    @Test
    void falls_back_to_client_default_on_conflict() {
        DialogueNodeSelectionStrategy strategy = strategySelector.updateChannelStrategy(
                channelA, ImmutableList.of(DialogueNodeSelectionStrategy.BALANCED));
        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.BALANCED);

        strategy = strategySelector.updateChannelStrategy(
                channelB, ImmutableList.of(DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE));

        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR);
    }

    @Test
    void only_considers_active_channels() {
        strategySelector.updateChannelStrategy(channelA, ImmutableList.of(DialogueNodeSelectionStrategy.BALANCED));
        DialogueNodeSelectionStrategy strategy = strategySelector.setActiveChannels(ImmutableList.of());

        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR);
    }
}
