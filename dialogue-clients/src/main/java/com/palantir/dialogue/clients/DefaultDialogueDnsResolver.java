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
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

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
            GaiError gaiError = extractGaiErrorString(e);
            log.warn("Unknown host '{}'", SafeArg.of("gaiError", gaiError), UnsafeArg.of("hostname", hostname), e);
            return ImmutableSet.of();
        }
    }

    // these strings were taken from glibc-2.39, but likely have not changed in quite a while
    // strings may be different on BSD systems like macos
    // TODO(dns): update this list to try to match against known strings on other platforms
    private enum GaiError {
        EAI_ADDRFAMILY("Address family for hostname not supported"),
        EAI_AGAIN("Temporary failure in name resolution"),
        EAI_BADFLAGS("Bad value for ai_flags"),
        EAI_FAIL("Non-recoverable failure in name resolution"),
        EAI_FAMILY("ai_family not supported"),
        EAI_MEMORY("Memory allocation failure"),
        EAI_NODATA("No address associated with hostname"),
        EAI_NONAME("Name or service not known"),
        EAI_SERVICE("Servname not supported for ai_socktype"),
        EAI_SOCKTYPE("ai_socktype not supported"),
        EAI_SYSTEM("System error"),
        EAI_INPROGRESS("Processing request in progress"),
        EAI_CANCELED("Request canceled"),
        EAI_NOTCANCELED("Request not canceled"),
        EAI_ALLDONE("All requests done"),
        EAI_INTR("Interrupted by a signal"),
        EAI_IDN_ENCODE("Parameter string not correctly encoded"),
        EAI_OVERFLOW("Result too large for supplied buffer"),
        CACHED(),
        UNKNOWN();

        private final Optional<String> errorMessage;

        GaiError() {
            this.errorMessage = Optional.empty();
        }

        GaiError(String errorMessage) {
            this.errorMessage = Optional.of(errorMessage);
        }

        @Safe
        String errorMessage() {
            return errorMessage.orElse(name());
        }
    }

    private static GaiError extractGaiErrorString(UnknownHostException exception) {
        try {
            StackTraceElement[] trace = exception.getStackTrace();
            if (trace.length > 0) {
                StackTraceElement top = trace[0];
                if ("java.net.InetAddress$CachedLookup".equals(top.getClassName())) {
                    return GaiError.CACHED;
                }

                for (GaiError error : GaiError.values()) {
                    if (error.errorMessage.isPresent()) {
                        if (exception.getMessage().contains(error.errorMessage.get())) {
                            return error;
                        }
                    }
                }
            }
            return GaiError.UNKNOWN;
        } catch (Exception e) {
            return GaiError.UNKNOWN;
        }
    }
}
