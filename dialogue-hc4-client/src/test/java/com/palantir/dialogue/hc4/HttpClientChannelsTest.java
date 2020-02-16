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
package com.palantir.dialogue.hc4;

import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.dialogue.AbstractChannelTest;
import com.palantir.dialogue.Channel;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.net.URL;
import java.nio.file.Paths;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class HttpClientChannelsTest extends AbstractChannelTest {

    private static final SslConfiguration SSL_CONFIG = SslConfiguration.of(
            Paths.get("src/test/resources/trustStore.jks"), Paths.get("src/test/resources/keyStore.jks"), "keystore");

    @Override
    protected Channel createChannel(URL baseUrl) {
        ServiceConfiguration serviceConf = ServiceConfiguration.builder()
                .addUris(baseUrl.toString())
                .security(SSL_CONFIG)
                .build();
        return HttpClientChannels.create(
                ClientConfigurations.of(serviceConf),
                UserAgent.of(UserAgent.Agent.of("test-service", "1.0.0")),
                new DefaultTaggedMetricRegistry());
    }
}
