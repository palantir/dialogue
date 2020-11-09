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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.random.SafeThreadLocalRandom;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Private class to centralize validation of params necessary to construct a dialogue channel. */
@Value.Immutable
interface Config {
    /**
     * This prefix may reconfigure several aspects of the client to work better in a world where requests are routed
     * through a service mesh like istio/envoy.
     */
    String MESH_PREFIX = "mesh-";

    Logger log = LoggerFactory.getLogger(Config.class);

    String channelName();

    ChannelFactory channelFactory();

    ClientConfiguration rawConfig();

    @Value.Derived
    default ClientConfiguration clientConf() {
        return ClientConfiguration.builder()
                .from(rawConfig())
                .uris(rawConfig().uris().stream().map(Config::stripMeshPrefix).collect(Collectors.toList()))
                .taggedMetricRegistry(
                        VersionedTaggedMetricRegistry.create(rawConfig().taggedMetricRegistry()))
                .build();
    }

    enum MeshMode {
        DEFAULT_NO_MESH,
        USE_EXTERNAL_MESH
    }

    @Value.Derived
    default MeshMode mesh() {
        long meshUris = rawConfig().uris().stream()
                .filter(s -> s.startsWith(MESH_PREFIX))
                .count();
        long normalUris = rawConfig().uris().stream()
                .filter(s -> !s.startsWith(MESH_PREFIX))
                .count();

        if (meshUris > 1) {
            log.warn(
                    "Not expecting multiple 'mesh-' prefixed uris - please double-check the uris",
                    SafeArg.of("meshUris", meshUris),
                    SafeArg.of("normalUris", normalUris),
                    SafeArg.of("channel", channelName()));
        }

        if (meshUris > 0) {
            if (normalUris == 0) {
                return MeshMode.USE_EXTERNAL_MESH;
            } else {
                log.warn(
                        "Some uris have 'mesh-' prefix but others don't, please pick one or the other",
                        SafeArg.of("meshUris", meshUris),
                        SafeArg.of("normalUris", normalUris),
                        SafeArg.of("channel", channelName()));
                // neither are perfect, but this one seems a bit safer
                return MeshMode.DEFAULT_NO_MESH;
            }
        } else {
            return MeshMode.DEFAULT_NO_MESH;
        }
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

    static String stripMeshPrefix(String input) {
        return input.startsWith(MESH_PREFIX) ? input.substring(MESH_PREFIX.length()) : input;
    }
}
