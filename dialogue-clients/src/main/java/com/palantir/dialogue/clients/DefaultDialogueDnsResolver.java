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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.immutables.value.Value;

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
            ExtractedGaiError gaiError = extractGaiErrorString(e);
            log.warn(
                    "Unknown host '{}'",
                    SafeArg.of("gaiErrorMessage", gaiError.getErrorMessage()),
                    SafeArg.of("gaiErrorType", gaiError.getErrorType()),
                    UnsafeArg.of("hostname", hostname),
                    e);
            return ImmutableSet.of();
        }
    }

    // these strings were taken from glibc-2.39, but likely have not changed in quite a while
    // strings may be different on BSD systems like macos
    // TODO(dns): update this list to try to match against known strings on other platforms
    private static final Map<String, String> EXPECTED_GAI_ERROR_STRINGS = ImmutableMap.<String, String>builder()
            .put("Address family for hostname not supported", "EAI_ADDRFAMILY")
            .put("Temporary failure in name resolution", "EAI_AGAIN")
            .put("Bad value for ai_flags", "EAI_BADFLAGS")
            .put("Non-recoverable failure in name resolution", "EAI_FAIL")
            .put("ai_family not supported", "EAI_FAMILY")
            .put("Memory allocation failure", "EAI_MEMORY")
            .put("No address associated with hostname", "EAI_NODATA")
            .put("Name or service not known", "EAI_NONAME")
            .put("Servname not supported for ai_socktype", "EAI_SERVICE")
            .put("ai_socktype not supported", "EAI_SOCKTYPE")
            .put("System error", "EAI_SYSTEM")
            .put("Processing request in progress", "EAI_INPROGRESS")
            .put("Request canceled", "EAI_CANCELED")
            .put("Request not canceled", "EAI_NOTCANCELED")
            .put("All requests done", "EAI_ALLDONE")
            .put("Interrupted by a signal", "EAI_INTR")
            .put("Parameter string not correctly encoded", "EAI_IDN_ENCODE")
            .put("Result too large for supplied buffer", "EAI_OVERFLOW")
            .buildOrThrow();

    @Value.Immutable
    interface ExtractedGaiError {
        @Value.Parameter
        String getErrorMessage();

        @Value.Parameter
        String getErrorType();
    }

    private static ExtractedGaiError extractGaiErrorString(UnknownHostException exception) {
        try {
            for (Map.Entry<String, String> entry : EXPECTED_GAI_ERROR_STRINGS.entrySet()) {
                if (exception.getMessage() != null && exception.getMessage().contains(entry.getKey())) {
                    return ImmutableExtractedGaiError.of(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            return ImmutableExtractedGaiError.of("unknown", "unknown");
        }
        return ImmutableExtractedGaiError.of("unknown", "unknown");
    }
}
