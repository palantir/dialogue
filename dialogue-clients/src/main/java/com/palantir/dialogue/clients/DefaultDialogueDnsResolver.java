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

import com.google.common.collect.ImmutableSet;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;

enum DefaultDialogueDnsResolver implements DialogueDnsResolver {
    INSTANCE;

    private static final SafeLogger log = SafeLoggerFactory.get(DefaultDialogueDnsResolver.class);

    @Override
    public ImmutableSet<InetAddress> resolve(String hostname) {
        Preconditions.checkNotNull(hostname, "hostname is required");
        try {
            InetAddress[] results = InetAddress.getAllByName(hostname);
            if (results == null || results.length == 0) {
                // Defensive check, this should not be possible
                return ImmutableSet.of();
            }
            return ImmutableSet.copyOf(results);
        } catch (UnknownHostException e) {
            log.warn("Unknown host '{}'", UnsafeArg.of("hostname", hostname), e);
            return ImmutableSet.of();
        }
    }
}
