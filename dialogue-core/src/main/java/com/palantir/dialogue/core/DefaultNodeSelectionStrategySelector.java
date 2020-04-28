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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@SuppressWarnings("NullAway")
public final class DefaultNodeSelectionStrategySelector implements NodeSelectionStrategySelector {
    private final DialogueNodeSelectionStrategy clientStrategy;
    private final AtomicReference<DialogueNodeSelectionStrategy> currentStrategy;
    private final ConcurrentHashMap<LimitedChannel, List<DialogueNodeSelectionStrategy>> strategyPerChannel =
            new ConcurrentHashMap<>();

    public DefaultNodeSelectionStrategySelector(NodeSelectionStrategy clientStrategy) {
        this.clientStrategy = DialogueNodeSelectionStrategy.of(clientStrategy);
        this.currentStrategy = new AtomicReference<>(this.clientStrategy);
    }

    @Override
    public DialogueNodeSelectionStrategy getCurrentStrategy() {
        return currentStrategy.get();
    }

    @Override
    public DialogueNodeSelectionStrategy updateChannelStrategy(
            LimitedChannel channel, List<DialogueNodeSelectionStrategy> updatedStrategies) {
        List<DialogueNodeSelectionStrategy> previousStrategies = strategyPerChannel.put(channel, updatedStrategies);
        if (updatedStrategies.isEmpty() || updatedStrategies.equals(previousStrategies)) {
            return currentStrategy.get();
        }
        return updateAndGetStrategy();
    }

    @Override
    public DialogueNodeSelectionStrategy setActiveChannels(List<LimitedChannel> channels) {
        Sets.difference(strategyPerChannel.keySet(), ImmutableSet.copyOf(channels))
                .forEach(strategyPerChannel::remove);
        return updateAndGetStrategy();
    }

    private DialogueNodeSelectionStrategy updateAndGetStrategy() {
        return currentStrategy.updateAndGet(_strategy -> {
            // TODO(forozco): improve strategy selection process to find the common intersection
            Collection<List<DialogueNodeSelectionStrategy>> requestedStrategies = strategyPerChannel.values();
            Set<DialogueNodeSelectionStrategy> firstChoiceStrategies = requestedStrategies.stream()
                    .flatMap(strategies -> strategies.stream()
                            .filter(strategy -> strategy != DialogueNodeSelectionStrategy.UNKNOWN)
                            .findFirst()
                            .map(Stream::of)
                            .orElseGet(Stream::empty))
                    .collect(ImmutableSet.toImmutableSet());
            if (firstChoiceStrategies.size() == 1) {
                return Iterables.getOnlyElement(firstChoiceStrategies);
            }
            return clientStrategy;
        });
    }
}
