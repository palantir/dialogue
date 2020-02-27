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

import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.dialogue.Channel;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@SuppressWarnings("ImmutableEnumChecker")
public enum Strategy {
    CONCURRENCY_LIMITER_ROUND_ROBIN(Strategy::concurrencyLimiter),
    CONCURRENCY_LIMITER_PIN_UNTIL_ERROR(Strategy::pinUntilError),
    UNLIMITED_ROUND_ROBIN(Strategy::unlimitedRoundRobin);

    private final BiFunction<Simulation, Supplier<List<SimulationServer>>, Channel> getChannel;

    Strategy(BiFunction<Simulation, Supplier<List<SimulationServer>>, Channel> getChannel) {
        this.getChannel = getChannel;
    }

    public Channel getChannel(Simulation simulation, Supplier<List<SimulationServer>> servers) {
        return getChannel.apply(simulation, servers);
    }

    private static Channel concurrencyLimiter(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        Random pseudo = new Random(3218974678L);
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            return DialogueChannel.builder()
                    .channels(channels)
                    .clientConfiguration(ClientConfiguration.builder()
                            .from(stubConfig())
                            .taggedMetricRegistry(sim.taggedMetrics())
                            .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                            .build())
                    .clock(sim.clock())
                    .random(pseudo)
                    .scheduler(sim.scheduler())
                    .build();
        });
    }

    private static Channel pinUntilError(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        Random psuedo = new Random(3218974678L);
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            return DialogueChannel.builder()
                    .channels(channels)
                    .clientConfiguration(ClientConfiguration.builder()
                            .from(stubConfig())
                            .taggedMetricRegistry(sim.taggedMetrics())
                            .nodeSelectionStrategy(NodeSelectionStrategy.PIN_UNTIL_ERROR)
                            .build())
                    .clock(sim.clock())
                    .random(psuedo)
                    .scheduler(sim.scheduler())
                    .build();
        });
    }

    private static Channel unlimitedRoundRobin(Simulation sim, Supplier<List<SimulationServer>> channelSupplier) {
        Random random = new Random(3218974678L);
        return RefreshingChannelFactory.RefreshingChannel.create(channelSupplier, channels -> {
            return DialogueChannel.builder()
                    .channels(channels)
                    .clientConfiguration(ClientConfiguration.builder()
                            .from(stubConfig())
                            .taggedMetricRegistry(sim.taggedMetrics())
                            .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                            .clientQoS(ClientConfiguration.ClientQoS.DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS)
                            .build())
                    .clock(sim.clock())
                    .random(random)
                    .scheduler(sim.scheduler())
                    .build();
        });
    }

    private static ClientConfiguration stubConfig() {
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(ServiceConfiguration.builder()
                        .uris(Collections.emptyList()) // nothing reads this!
                        .security(SslConfiguration.of(
                                Paths.get("../dialogue-client-test-lib/src/main/resources/trustStore.jks"),
                                Paths.get("../dialogue-client-test-lib/src/main/resources/keyStore.jks"),
                                "keystore"))
                        .build()))
                .userAgent(UserAgent.of(UserAgent.Agent.of("foo", "1.0.0")))
                .build();
    }
}
