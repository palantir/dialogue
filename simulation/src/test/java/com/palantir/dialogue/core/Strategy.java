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
import com.palantir.logsafe.Preconditions;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("ImmutableEnumChecker")
public enum Strategy {
    CONCURRENCY_LIMITER_ROUND_ROBIN(Strategy::concurrencyLimiter),
    CONCURRENCY_LIMITER_PIN_UNTIL_ERROR(Strategy::pinUntilError),
    UNLIMITED_ROUND_ROBIN(Strategy::unlimitedRoundRobin);

    private static final ClientConfiguration STUB_CONFIG = stubConfig();
    private final Consumer<ClientConfiguration.Builder> applyConfig;

    Strategy(Consumer<ClientConfiguration.Builder> applyConfig) {
        this.applyConfig = applyConfig;
    }

    public Channel getChannel(Simulation simulation, Supplier<Map<String, SimulationServer>> servers) {
        return refreshingChannel(simulation, servers);
    }

    public Supplier<Channel> getSticky2NonReloading(Simulation simulation, Map<String, SimulationServer> servers) {
        Preconditions.checkArgument(servers.size() == 1, "Only one server supported");
        return dialogueChannelWithDefaults(simulation, servers).stickyChannels();
    }

    private static void concurrencyLimiter(ClientConfiguration.Builder configBuilder) {
        configBuilder
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .failedUrlCooldown(Duration.ofMillis(200));
    }

    private static void pinUntilError(ClientConfiguration.Builder configBuilder) {
        configBuilder.nodeSelectionStrategy(NodeSelectionStrategy.PIN_UNTIL_ERROR);
    }

    private static void unlimitedRoundRobin(ClientConfiguration.Builder configBuilder) {
        configBuilder
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .failedUrlCooldown(Duration.ofMillis(200))
                .clientQoS(ClientConfiguration.ClientQoS.DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS);
    }

    private Channel refreshingChannel(Simulation sim, Supplier<Map<String, SimulationServer>> channelSupplier) {
        return RefreshingChannelFactory.RefreshingChannel.create(
                channelSupplier, channels -> dialogueChannelWithDefaults(sim, channels));
    }

    private DialogueChannel dialogueChannelWithDefaults(Simulation sim, Map<String, SimulationServer> channelSupplier) {
        ClientConfiguration.Builder confBuilder = ClientConfiguration.builder()
                .uris(channelSupplier.keySet())
                .from(STUB_CONFIG)
                .taggedMetricRegistry(sim.taggedMetrics());
        applyConfig.accept(confBuilder);
        return DialogueChannel.builder()
                .channelName(SimulationUtils.CHANNEL_NAME)
                .clientConfiguration(confBuilder.build())
                .factory(args -> channelSupplier.get(args.uri()))
                .random(sim.pseudoRandom())
                .scheduler(sim.scheduler())
                .ticker(sim.clock())
                .build();
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
