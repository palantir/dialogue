/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

class ApacheHttpClientChannelsTimeoutTest {
    private static final String NAME = "name";

    @Test
    void testConnectionTimeoutUsed() {
        Timeout connectTimeout = Timeout.ofSeconds(15);
        Timeout socketTimeout = Timeout.ofSeconds(30);
        assertThat(ApacheHttpClientChannels.getHandshakeTimeout(connectTimeout, socketTimeout, NAME))
                .as("Expected the connect timeout")
                .isEqualTo(connectTimeout);
    }

    @Test
    void testDefaultTimeoutUsed() {
        Timeout connectTimeout = Timeout.ofMilliseconds(1);
        Timeout socketTimeout = Timeout.ofSeconds(30);
        assertThat(ApacheHttpClientChannels.getHandshakeTimeout(connectTimeout, socketTimeout, NAME))
                .as("Default timeout expected when the connect timeout is low and the socket timeout is large")
                .isEqualTo(ApacheHttpClientChannels.DEFAULT_HANDSHAKE_TIMEOUT);
    }

    @Test
    void testSocketTimeoutUsed() {
        Timeout connectTimeout = Timeout.ofMilliseconds(1);
        Timeout socketTimeout = Timeout.ofSeconds(5);
        assertThat(ApacheHttpClientChannels.getHandshakeTimeout(connectTimeout, socketTimeout, NAME))
                .as("The lesser of the socket timeout and default timeout should be used: Expected the socket timeout")
                .isEqualTo(socketTimeout);
    }
}
