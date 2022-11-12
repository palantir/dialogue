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
package com.palantir.dialogue.hc5;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.AbstractProxyConfigTest;
import com.palantir.dialogue.Channel;
import mockwebserver3.MockWebServer;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockWebServerExtension.class)
public final class ApacheProxyConfigTest extends AbstractProxyConfigTest {
    ApacheProxyConfigTest(MockWebServer server, MockWebServer proxyServer) {
        super(server, proxyServer);
    }

    @Override
    protected Channel create(ClientConfiguration config) {
        return ApacheHttpClientChannels.create(config);
    }
}
