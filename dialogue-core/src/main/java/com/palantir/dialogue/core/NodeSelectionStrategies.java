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
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.util.Optional;
import javax.annotation.Nullable;

final class NodeSelectionStrategies {

    static LimitedChannel create(
            Config cf, ImmutableList<LimitedChannel> channels, @Nullable LimitedChannel previousChannel) {

        if (channels.isEmpty()) {
            return new ZeroUriNodeSelectionChannel(cf.channelName());
        }

        if (channels.size() == 1) {
            // there is no strategy if we only have one node
            return channels.get(0);
        }

        NodeSelectionStrategy nodeSelectionStrategy = cf.clientConf().nodeSelectionStrategy();
        switch (nodeSelectionStrategy) {
            case PIN_UNTIL_ERROR:
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                DialoguePinuntilerrorMetrics pinuntilerrorMetrics =
                        DialoguePinuntilerrorMetrics.of(cf.clientConf().taggedMetricRegistry());
                // Previously pin until error, so we should preserve our previous location
                if (previousChannel instanceof PinUntilErrorNodeSelectionStrategyChannel) {
                    PinUntilErrorNodeSelectionStrategyChannel previousPinUntilError =
                            (PinUntilErrorNodeSelectionStrategyChannel) previousChannel;
                    return PinUntilErrorNodeSelectionStrategyChannel.of(
                            Optional.of(previousPinUntilError.getCurrentChannel()),
                            nodeSelectionStrategy,
                            channels,
                            pinuntilerrorMetrics,
                            cf.random(),
                            cf.channelName());
                }
                return PinUntilErrorNodeSelectionStrategyChannel.of(
                        Optional.empty(),
                        nodeSelectionStrategy,
                        channels,
                        pinuntilerrorMetrics,
                        cf.random(),
                        cf.channelName());
            case ROUND_ROBIN:
                // When people ask for 'ROUND_ROBIN', they usually just want something to load balance better.
                // We used to have a naive RoundRobinChannel, then tried RandomSelection and now use this heuristic:
                return new BalancedNodeSelectionStrategyChannel(
                        channels, cf.random(), cf.ticker(), cf.clientConf().taggedMetricRegistry(), cf.channelName());
        }
        throw new SafeRuntimeException("Unknown NodeSelectionStrategy", SafeArg.of("unknown", nodeSelectionStrategy));
    }

    private NodeSelectionStrategies() {}
}
