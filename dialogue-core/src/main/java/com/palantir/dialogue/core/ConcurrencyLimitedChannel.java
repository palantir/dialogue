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

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.OptionalDouble;

interface ConcurrencyLimitedChannel extends LimitedChannel {

    static ConcurrencyLimitedChannel create(Config cf, LimitedChannel channel, int uriIndex) {
        ClientConfiguration.ClientQoS clientQoS = cf.clientConf().clientQoS();
        switch (clientQoS) {
            case ENABLED:
                TaggedMetricRegistry metrics = cf.clientConf().taggedMetricRegistry();
                return new ConcurrencyLimitedChannelImpl(
                        channel, ConcurrencyLimitedChannelImpl.createLimiter(), cf.channelName(), uriIndex, metrics);
            case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                return new NoOpConcurrencyLimitedChannel(channel);
        }
        throw new SafeIllegalStateException(
                "Encountered unknown client QoS configuration", SafeArg.of("ClientQoS", clientQoS));
    }

    int getInflight();

    OptionalDouble getMax();
}
