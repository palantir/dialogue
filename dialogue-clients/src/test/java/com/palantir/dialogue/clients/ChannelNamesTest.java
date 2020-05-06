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

package com.palantir.dialogue.clients;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.refreshable.Refreshable;
import org.junit.jupiter.api.Test;

class ChannelNamesTest {

    @Test
    void name_is_concise_by_default() {
        String channelName = ChannelNames.reloading(
                "multipass",
                ImmutableReloadingParams.builder().scb(Refreshable.only(null)).build());
        assertThat(channelName).isEqualTo("dialogue-multipass");
    }

    @Test
    void verbose_if_necessary() {
        String channelName = ChannelNames.reloading(
                "multipass",
                ImmutableReloadingParams.builder()
                        .scb(Refreshable.only(null))
                        .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                        .clientQoS(ClientConfiguration.ClientQoS.DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS)
                        .serverQoS(ClientConfiguration.ServerQoS.PROPAGATE_429_and_503_TO_CALLER)
                        .retryOnTimeout(ClientConfiguration.RetryOnTimeout.DANGEROUS_ENABLE_AT_RISK_OF_RETRY_STORMS)
                        .maxNumRetries(37)
                        .build());
        assertThat(channelName)
                .isEqualTo("dialogue-multipass-ROUND_ROBIN-MAX_RETRIES_37-DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS-"
                        + "PROPAGATE_429_and_503_TO_CALLER-DANGEROUS_ENABLE_AT_RISK_OF_RETRY_STORMS");
    }
}
