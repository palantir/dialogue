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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RoutingAttachments;
import com.palantir.dialogue.RoutingAttachments.HostId;
import com.palantir.dialogue.TestResponse;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeSelectionStrategyChannelTest {

    @Spy
    private NodeSelectionStrategyChooser strategySelector = new NodeSelectionStrategyChooser() {
        @Override
        public Optional<DialogueNodeSelectionStrategy> updateAndGet(
                List<DialogueNodeSelectionStrategy> updatedStrategies) {
            return NodeSelectionStrategyChannel.getFirstKnownStrategy(updatedStrategies);
        }
    };

    @Mock
    private LimitedChannel channel1;

    @Mock
    private LimitedChannel channel2;

    @Mock
    private Ticker clock;

    private String channelName = "channelName";
    private Random pseudo = new Random(12893712L);
    private NodeSelectionStrategyChannel channel;

    @BeforeEach
    void beforeEach() {}

    @Test
    void updates_strategy_on_response() {
        ImmutableList<LimitedChannel> channels = ImmutableList.of(channel1, channel2);
        channel = new NodeSelectionStrategyChannel(
                strategySelector,
                DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE,
                channelName,
                pseudo,
                clock,
                new DefaultTaggedMetricRegistry(),
                channels);

        when(channel1.maybeExecute(any(), any()))
                .thenReturn(Optional.of(Futures.immediateFuture(
                        new TestResponse().code(200).withHeader("Node-Selection-Strategy", "BALANCED,FOO"))));

        assertThat(channel.maybeExecute(null, Request.builder().build())).isPresent();
        verify(strategySelector, times(1))
                .updateAndGet(eq(ImmutableList.of(
                        DialogueNodeSelectionStrategy.BALANCED, DialogueNodeSelectionStrategy.UNKNOWN)));
    }

    @Test
    @Disabled
    void updates_strategy_on_response_on_request_with_routing_key() {
        ImmutableList<LimitedChannel> channels = ImmutableList.of(channel1, channel2);
        channel = new NodeSelectionStrategyChannel(
                strategySelector,
                DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE,
                channelName,
                pseudo,
                clock,
                new DefaultTaggedMetricRegistry(),
                channels);

        when(channel1.maybeExecute(any(), any()))
                .thenReturn(Optional.of(Futures.immediateFuture(
                        new TestResponse().code(200).withHeader("Node-Selection-Strategy", "BALANCED,FOO"))));

        Request request = Request.builder().build();
        request.attachments().put(RoutingAttachments.EXECUTE_ON_HOST_ID_KEY, HostId.of(0));
        assertThat(channel.maybeExecute(null, request)).isPresent();
        verify(strategySelector, times(1))
                .updateAndGet(eq(ImmutableList.of(
                        DialogueNodeSelectionStrategy.BALANCED, DialogueNodeSelectionStrategy.UNKNOWN)));
    }
}
