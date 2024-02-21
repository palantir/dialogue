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

import com.google.common.collect.ImmutableSetMultimap;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.logsafe.DoNotLog;
import java.net.InetAddress;
import org.immutables.value.Value;

@DoNotLog
@Value.Immutable
interface ServicesConfigBlockWithResolvedHosts {
    @Value.Parameter
    ServicesConfigBlock scb();

    // maps hostname (not service name) -> resolved IP addresses
    @Value.Parameter
    ImmutableSetMultimap<String, InetAddress> resolvedHosts();

    static ServicesConfigBlockWithResolvedHosts empty() {
        return ImmutableServicesConfigBlockWithResolvedHosts.of(
                ServicesConfigBlock.builder().build(), ImmutableSetMultimap.of());
    }
}