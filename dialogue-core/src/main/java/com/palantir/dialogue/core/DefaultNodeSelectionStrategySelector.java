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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("NullAway")
final class DefaultNodeSelectionStrategySelector implements NodeSelectionStrategySelector {
    private final AtomicReference<DialogueNodeSelectionStrategy> currentStrategy;
    private final DialogueNodeselectionMetrics metrics;
    private final ConcurrentHashMap<LimitedChannel, List<DialogueNodeSelectionStrategy>> strategyPerChannel =
            new ConcurrentHashMap<>();

    DefaultNodeSelectionStrategySelector(NodeSelectionStrategy initialStrategy, DialogueNodeselectionMetrics metrics) {
        this.currentStrategy = new AtomicReference<>(DialogueNodeSelectionStrategy.of(initialStrategy));
        this.metrics = metrics;
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

    /**
     * returns the requested strategy with the lowest "score", where score is the sum of each strategy's position
     * in each nodes request list.
     *
     * In case of ties, fall back to the previous strategy.
     */
    private DialogueNodeSelectionStrategy updateAndGetStrategy() {
        return currentStrategy.updateAndGet(previousStrategy -> {
            Collection<List<DialogueNodeSelectionStrategy>> allRequestedStrategies = strategyPerChannel.values();
            Map<DialogueNodeSelectionStrategy, Integer> scorePerStrategy = Arrays.stream(
                            DialogueNodeSelectionStrategy.values())
                    .filter(strategy -> strategy != DialogueNodeSelectionStrategy.UNKNOWN)
                    .collect(Collectors.toMap(Function.identity(), strategy -> allRequestedStrategies.stream()
                            .mapToInt(requestedStrategies -> {
                                int score = requestedStrategies.indexOf(strategy);
                                return score == -1 ? DialogueNodeSelectionStrategy.values().length : score;
                            })
                            .sum()));

            int minScore = Collections.min(scorePerStrategy.values());
            Set<DialogueNodeSelectionStrategy> minScoreStrategies = scorePerStrategy.entrySet().stream()
                    .filter(entry -> entry.getValue() == minScore)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            if (minScoreStrategies.size() == 1) {
                DialogueNodeSelectionStrategy proposedStrategy = Iterables.getOnlyElement(minScoreStrategies);
                if (!proposedStrategy.equals(previousStrategy)) {
                    metrics.strategy(proposedStrategy.name()).mark();
                    return proposedStrategy;
                }
            }

            return previousStrategy;
        });
    }
}
