/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.dialogue.DialogueResponseTimeoutException.isResponseTimeout;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

class DialogueResponseTimeoutExceptionTest {

    @Test
    void readTimeoutsAreResponseTimeouts() {
        assertThat(isResponseTimeout(new SocketTimeoutException("Read timed out")))
                .as("read timeout")
                .isTrue();
        assertThat(isResponseTimeout(new SocketTimeoutException()))
                .as("socket timeout without a message")
                .isTrue();
    }

    @Test
    void connectionTimedOutAfterConnectingIsResponseTimeout() {
        assertThat(isResponseTimeout(new SocketException("Connection timed out")))
                .isTrue();
    }

    @Test
    void connectTimeoutsAreNotResponseTimeouts() {
        assertThat(isResponseTimeout(new SocketTimeoutException("connect timed out")))
                .as("socket connect timeout")
                .isFalse();
        assertThat(isResponseTimeout(new SafeConnectTimeoutException(new SocketTimeoutException("Connect timed out"))))
                .as("Dialogue connect timeout")
                .isFalse();
        assertThat(isResponseTimeout(new ConnectException("Connection timed out")))
                .as("TCP timeout while connecting")
                .isFalse();
    }

    @Test
    void otherFailuresAreNotResponseTimeouts() {
        assertThat(isResponseTimeout(new SocketException("Connection reset")))
                .as("connection reset")
                .isFalse();
        assertThat(isResponseTimeout(new IOException("Broken pipe")))
                .as("other I/O failure")
                .isFalse();
        assertThat(isResponseTimeout(null)).as("no failure").isFalse();
    }
}
