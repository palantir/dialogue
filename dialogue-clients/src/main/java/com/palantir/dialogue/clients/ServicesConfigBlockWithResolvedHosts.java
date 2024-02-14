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
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.net.InetAddress;
import java.util.Objects;

@DoNotLog
final class ServicesConfigBlockWithResolvedHosts {
    private static final SafeLogger log = SafeLoggerFactory.get(ServicesConfigBlockWithResolvedHosts.class);

    private final ServicesConfigBlock scb;
    // maps hostname (not service name) -> resolved IP addresses
    private final ImmutableSetMultimap<String, InetAddress> resolvedHosts;

    ServicesConfigBlockWithResolvedHosts(
            ServicesConfigBlock scb, ImmutableSetMultimap<String, InetAddress> resolvedHosts) {
        this.scb = scb;
        this.resolvedHosts = resolvedHosts;
    }

    ServicesConfigBlock scb() {
        return scb;
    }

    ImmutableSetMultimap<String, InetAddress> resolvedHosts() {
        return resolvedHosts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServicesConfigBlockWithResolvedHosts that = (ServicesConfigBlockWithResolvedHosts) o;
        return Objects.equals(scb, that.scb) && Objects.equals(resolvedHosts, that.resolvedHosts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scb, resolvedHosts);
    }

    @Override
    public String toString() {
        return "ServicesConfigBlockWithResolvedHosts{" + "scb=" + scb + ", resolvedHosts=" + resolvedHosts + '}';
    }
}
