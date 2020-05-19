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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.util.Optional;

final class Strategies {

    static LimitedChannel create(Config cf, ImmutableList<LimitedChannel> channels) {
        if (channels.isEmpty()) {
            return new ZeroUriNodeSelectionChannel(cf.channelName());
        }

        if (channels.size() == 1) {
            return channels.get(0);
        }

        DialoguePinuntilerrorMetrics pinuntilerrorMetrics =
                DialoguePinuntilerrorMetrics.of(cf.clientConf().taggedMetricRegistry());
        switch (cf.clientConf().nodeSelectionStrategy()) {
            case PIN_UNTIL_ERROR:
                return PinUntilErrorNodeSelectionStrategyChannel.of(
                        Optional.empty(),
                        DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR,
                        channels,
                        pinuntilerrorMetrics,
                        cf.random(),
                        cf.ticker(),
                        cf.channelName());
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                return PinUntilErrorNodeSelectionStrategyChannel.of(
                        Optional.empty(),
                        DialogueNodeSelectionStrategy.PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE,
                        channels,
                        pinuntilerrorMetrics,
                        cf.random(),
                        cf.ticker(),
                        cf.channelName());

            case ROUND_ROBIN:
                // When people ask for 'ROUND_ROBIN', they usually just want something to load balance better.
                // We used to have a naive RoundRobinChannel, then tried RandomSelection and now use this heuristic:
                return new BalancedNodeSelectionStrategyChannel(
                        channels, cf.random(), cf.ticker(), cf.clientConf().taggedMetricRegistry(), cf.channelName());
        }
        throw new SafeRuntimeException(
                "Unknown NodeSelectionStrategy",
                SafeArg.of("unknown", cf.clientConf().nodeSelectionStrategy()));
    }

    private Strategies() {}
}
