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
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.dialogue.Channel;
import java.net.URI;
import java.nio.file.Paths;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class DialogueTest {
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("../dialogue-client-test-lib/src/main/resources/trustStore.jks"),
            Paths.get("../dialogue-client-test-lib/src/main/resources/keyStore.jks"),
            "keystore");

    private final ClientConfiguration LEGACY = createTestConfig("node1", "node2");
    private final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("foo", "1.0.0"));

    @Test
    void can_create_a_raw_apache_channel() {
        try (ClientPool clientPool = Dialogue.newClientPool()) {
            Assertions.assertThatThrownBy(() -> {
                        Channel node1 = clientPool.rawChannel(URI.create("node1"), () -> {
                            return ClientConfig.builder()
                                    .from(LEGACY)
                                    .rawClientType(ClientConfig.RawClientType.APACHE)
                                    .userAgent(USER_AGENT)
                                    .build();
                        });
                    })
                    .hasMessageContaining("APACHE"); // TODO service loading??
        }
    }

    @Test
    void dialogue_can_reflectively_instantiate_stuff() {
        Dialogue.instantiate()
    }

    private static ClientConfiguration createTestConfig(String... uri) {
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(
                        ImmutableList.copyOf(uri),
                        SslSocketFactories.createSslSocketFactory(SSL_CONFIG),
                        SslSocketFactories.createX509TrustManager(SSL_CONFIG)))
                .maxNumRetries(0)
                .build();
    }
}
