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
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.Channel;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@SuppressWarnings("ImmutableEnumChecker")
public enum Strategy {
    CONCURRENCY_LIMITER_ROUND_ROBIN(Strategy::concurrencyLimiter),
    CONCURRENCY_LIMITER_PIN_UNTIL_ERROR(Strategy::pinUntilError),
    UNLIMITED_ROUND_ROBIN(Strategy::unlimitedRoundRobin);

    private final BiFunction<Simulation, Supplier<Map<String, SimulationServer>>, Channel> getChannel;

    Strategy(BiFunction<Simulation, Supplier<Map<String, SimulationServer>>, Channel> getChannel) {
        this.getChannel = getChannel;
    }

    public Channel getChannel(Simulation simulation, Supplier<Map<String, SimulationServer>> servers) {
        return getChannel.apply(simulation, servers);
    }

    private static Channel concurrencyLimiter(Simulation sim, Supplier<Map<String, SimulationServer>> channelSupplier) {
        return withDefaults(sim, channelSupplier, configBuilder -> configBuilder
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .failedUrlCooldown(Duration.ofMillis(200)));
    }

    private static Channel pinUntilError(Simulation sim, Supplier<Map<String, SimulationServer>> channelSupplier) {
        return withDefaults(
                sim,
                channelSupplier,
                configBuilder -> configBuilder.nodeSelectionStrategy(NodeSelectionStrategy.PIN_UNTIL_ERROR));
    }

    private static Channel unlimitedRoundRobin(
            Simulation sim, Supplier<Map<String, SimulationServer>> channelSupplier) {
        return withDefaults(sim, channelSupplier, configBuilder -> configBuilder
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .failedUrlCooldown(Duration.ofMillis(200))
                .clientQoS(ClientConfiguration.ClientQoS.DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS));
    }

    private static Channel withDefaults(
            Simulation sim,
            Supplier<Map<String, SimulationServer>> channelSupplier,
            UnaryOperator<ClientConfiguration.Builder> applyConfig) {
        DialogueChannel channel = DialogueChannel.builder()
                .channelName(SimulationUtils.CHANNEL_NAME)
                .clientConfiguration(applyConfig
                        .apply(ClientConfiguration.builder()
                                .uris(ImmutableList.copyOf(channelSupplier.get().keySet()))
                                .from(stubConfig())
                                .taggedMetricRegistry(sim.taggedMetrics()))
                        .build())
                .channelFactory(uri -> channelSupplier.get().get(uri))
                .random(sim.pseudoRandom())
                .scheduler(sim.scheduler())
                .build();

        return RefreshingChannelFactory.RefreshingChannel.create(
                () -> channelSupplier.get().keySet(), uris -> {
                    channel.updateUris(uris);
                    return channel;
                });
    }

    private static ClientConfiguration stubConfig() {
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .uris(Collections.emptyList()) // nothing reads this!
                        .security(SslConfiguration.of(
                                Paths.get("../dialogue-test-common/src/main/resources/trustStore.jks"),
                                Paths.get("../dialogue-test-common/src/main/resources/keyStore.jks"),
                                "keystore"))
                        .build()))
                .userAgent(UserAgent.of(UserAgent.Agent.of("foo", "1.0.0")))
                .build();
    }
}
