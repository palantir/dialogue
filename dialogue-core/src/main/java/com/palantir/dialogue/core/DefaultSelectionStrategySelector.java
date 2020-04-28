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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultSelectionStrategySelector implements SelectionStrategySelector {
    private final DialogueNodeSelectionStrategy clientStrategy;
    private final AtomicReference<DialogueNodeSelectionStrategy> currentStrategy;
    private final ConcurrentHashMap<LimitedChannel, DialogueNodeSelectionStrategy> strategyPerChannel =
            new ConcurrentHashMap<>();

    public DefaultSelectionStrategySelector(NodeSelectionStrategy clientStrategy) {
        this.clientStrategy = DialogueNodeSelectionStrategy.of(clientStrategy);
        this.currentStrategy = new AtomicReference<>(this.clientStrategy);
    }

    @Override
    public DialogueNodeSelectionStrategy get() {
        return currentStrategy.get();
    }

    @Override
    public DialogueNodeSelectionStrategy updateAndGet(LimitedChannel channel, String strategyUpdate) {
        DialogueNodeSelectionStrategy strategy = DialogueNodeSelectionStrategy.valueOf(strategyUpdate);
        if (strategyPerChannel.getOrDefault(channel, strategy).equals(strategy)) {
            return currentStrategy.get();
        }

        strategyPerChannel.put(channel, strategy);
        return updateAndGetStrategy();
    }

    @Override
    public DialogueNodeSelectionStrategy updateAndGet(ImmutableList<LimitedChannel> channels) {
        channels.forEach(strategyPerChannel::remove);
        return updateAndGetStrategy();
    }

    private DialogueNodeSelectionStrategy updateAndGetStrategy() {
        return currentStrategy.updateAndGet(_strategy -> {
            Set<DialogueNodeSelectionStrategy> strategies = new HashSet<>(strategyPerChannel.values());
            if (strategies.size() == 1) {
                return Iterables.getOnlyElement(strategies);
            }
            return clientStrategy;
        });
    }
}
