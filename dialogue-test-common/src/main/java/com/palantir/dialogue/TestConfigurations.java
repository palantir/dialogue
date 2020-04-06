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
package com.palantir.dialogue;

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.nio.file.Paths;

public final class TestConfigurations {

    public static final UserAgent AGENT = UserAgent.of(UserAgent.Agent.of("test", "0.0.1"));
    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("../dialogue-test-common/src/main/resources/trustStore.jks"),
            Paths.get("../dialogue-test-common/src/main/resources/keyStore.jks"),
            "keystore");

    public static ClientConfiguration create(String... uris) {
        return ClientConfiguration.builder()
                .from(ClientConfigurations.of(
                        ImmutableList.copyOf(uris),
                        SslSocketFactories.createSslSocketFactory(SSL_CONFIG),
                        SslSocketFactories.createX509TrustManager(SSL_CONFIG)))
                .maxNumRetries(0)
                .userAgent(AGENT)
                .taggedMetricRegistry(new DefaultTaggedMetricRegistry())
                .build();
    }

    private TestConfigurations() {}
}
