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
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
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
        strategySelector = new DefaultNodeSelectionStrategySelector(
                NodeSelectionStrategy.PIN_UNTIL_ERROR,
                DialogueNodeselectionMetrics.of(new DefaultTaggedMetricRegistry()));
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
    void falls_back_to_previous_on_conflict() {
        DialogueNodeSelectionStrategy strategy;

        strategy = strategySelector.updateChannelStrategy(
                channelA, ImmutableList.of(DialogueNodeSelectionStrategy.BALANCED));
        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.BALANCED);

        strategy = strategySelector.updateChannelStrategy(
                channelB, ImmutableList.of(DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE));

        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.BALANCED);
    }

    @Test
    void ignores_unknown_strategy() {
        strategySelector.updateChannelStrategy(
                channelA,
                ImmutableList.of(DialogueNodeSelectionStrategy.UNKNOWN, DialogueNodeSelectionStrategy.BALANCED));
        DialogueNodeSelectionStrategy strategy = strategySelector.updateChannelStrategy(
                channelB, ImmutableList.of(DialogueNodeSelectionStrategy.BALANCED));
        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.BALANCED);
    }

    @Test
    void only_considers_active_channels() {
        DialogueNodeSelectionStrategy strategy;
        // Initially prefers PuE
        strategy = strategySelector.updateChannelStrategy(
                channelA,
                ImmutableList.of(
                        DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR, DialogueNodeSelectionStrategy.BALANCED));
        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR);

        // Switches to Balance upon seeing another node that requests Balance
        strategy = strategySelector.updateChannelStrategy(
                channelB, ImmutableList.of(DialogueNodeSelectionStrategy.BALANCED));
        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.BALANCED);

        // Switches back to PuE once that node disappears
        strategy = strategySelector.setActiveChannels(ImmutableList.of(channelA));
        assertThat(strategy).isEqualTo(DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR);
    }
}
