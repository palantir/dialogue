/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableSet;
import com.palantir.logsafe.exceptions.SafeNullPointerException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class DefaultDialogueDnsResolverTest {

    @Test
    void nullInput() {
        assertThatThrownBy(() -> resolve(null))
                .isInstanceOf(SafeNullPointerException.class)
                .hasMessageContaining("hostname is required");
    }

    @Test
    void localhost() throws UnknownHostException {
        assertThat(resolve("localhost")).contains(InetAddress.getByAddress("localhost", new byte[] {127, 0, 0, 1}));
    }

    @Test
    void ipv4() throws UnknownHostException {
        assertThat(resolve("127.0.0.1")).containsExactly(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
    }

    @Test
    void ipv6_brackets() throws UnknownHostException {
        assertThat(resolve("[::1]"))
                .containsExactly(InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}));
    }

    @Test
    void ipv6_noBrackets() throws UnknownHostException {
        assertThat(resolve("[::1]"))
                .containsExactly(InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}));
    }

    @Test
    void malformedIpv6_brackets() {
        assertThat(resolve("[::z]")).isEmpty();
    }

    @Test
    void malformedIpv6_noBrackets() {
        assertThat(resolve("::z")).isEmpty();
    }

    private static ImmutableSet<InetAddress> resolve(String hostname) {
        return DefaultDialogueDnsResolver.INSTANCE.resolve(hostname);
    }
}
