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

import com.github.benmanes.caffeine.cache.Ticker;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.logsafe.Preconditions;
import com.palantir.random.SafeThreadLocalRandom;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.immutables.value.Value;

/** Private class to centralize validation of params necessary to construct a dialogue channel. */
@Value.Immutable
interface Config {
    String channelName();

    ChannelFactory channelFactory();

    ClientConfiguration rawConfig();

    @Value.Derived
    default ClientConfiguration clientConf() {
        return ClientConfiguration.builder()
                .from(rawConfig())
                .taggedMetricRegistry(
                        new VersionedTaggedMetricRegistry(rawConfig().taggedMetricRegistry()))
                .build();
    }

    @Value.Default
    default Random random() {
        return SafeThreadLocalRandom.get();
    }

    @Value.Default
    default Supplier<ScheduledExecutorService> scheduler() {
        return RetryingChannel.sharedScheduler;
    }

    @Value.Default
    default Ticker ticker() {
        return Ticker.systemTicker();
    }

    @Value.Default
    default int maxQueueSize() {
        return 100_000;
    }

    @Value.Check
    default void check() {
        Preconditions.checkArgument(maxQueueSize() > 0, "maxQueueSize must be positive");
        Preconditions.checkArgument(rawConfig().userAgent().isPresent(), "rawConfig.userAgent() must be specified");
        Preconditions.checkArgument(
                rawConfig().retryOnSocketException() == ClientConfiguration.RetryOnSocketException.ENABLED,
                "Retries on socket exceptions cannot be disabled without disabling retries entirely.");
    }
}
