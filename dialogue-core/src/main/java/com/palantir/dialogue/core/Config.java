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
import com.palantir.conjure.java.client.config.ClientConfiguration.ClientQoS;
import com.palantir.logsafe.DoNotLog;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.random.SafeThreadLocalRandom;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import org.immutables.value.Value;

/** Private class to centralize validation of params necessary to construct a dialogue channel. */
@DoNotLog
@Value.Immutable
interface Config {

    String channelName();

    DialogueChannelFactory channelFactory();

    ClientConfiguration rawConfig();

    @Value.Default
    default List<TargetUri> uris() {
        return rawConfig().uris().stream()
                .map(MeshMode::stripMeshPrefix)
                .map(uri -> ImmutableTargetUri.builder()
                        .uri(uri)
                        .host(ImmutableTargetHost.builder()
                                .hostname(URI.create(uri).getHost())
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    @Value.Derived
    default ClientConfiguration clientConf() {
        return ClientConfiguration.builder()
                .from(rawConfig())
                .uris(uris().stream().map(TargetUri::uri).collect(Collectors.toList()))
                .taggedMetricRegistry(rawConfig().taggedMetricRegistry())
                .build();
    }

    @Value.Derived
    default MeshMode mesh() {
        return MeshMode.fromUris(rawConfig().uris(), SafeArg.of("channelName", channelName()));
    }

    @Value.Derived
    default boolean isConcurrencyLimitingEnabled() {
        return clientConf().clientQoS() == ClientQoS.ENABLED && mesh() != MeshMode.USE_EXTERNAL_MESH;
    }

    @Value.Default
    default Random random() {
        return SafeThreadLocalRandom.get();
    }

    @Value.Default
    default ScheduledExecutorService scheduler() {
        return RetryingChannel.sharedScheduler.get();
    }

    @Value.Default
    default Ticker ticker() {
        return Ticker.systemTicker();
    }

    @Value.Default
    default int maxQueueSize() {
        return 100_000;
    }

    OptionalInt overrideSingleHostIndex();

    @Value.Check
    default void check() {
        Preconditions.checkArgument(maxQueueSize() > 0, "maxQueueSize must be positive");
        Preconditions.checkArgument(rawConfig().userAgent().isPresent(), "userAgent must be specified");
        Preconditions.checkArgument(
                rawConfig().retryOnSocketException() == ClientConfiguration.RetryOnSocketException.ENABLED,
                "Retries on socket exceptions cannot be disabled without disabling retries entirely.");

        if (rawConfig().uris().size() > 1 && overrideSingleHostIndex().isPresent()) {
            throw new SafeIllegalArgumentException(
                    "overrideHostIndex is only permitted when there is a single uri",
                    SafeArg.of("numUris", rawConfig().uris().size()));
        }
    }

    @Value.Immutable
    interface TargetHost {
        String hostname();

        Optional<InetAddress> resolved();
    }

    @Value.Immutable
    interface TargetUri {
        /** URI which includes {@link TargetHost#hostname()}. */
        String uri();

        TargetHost host();
    }
}
