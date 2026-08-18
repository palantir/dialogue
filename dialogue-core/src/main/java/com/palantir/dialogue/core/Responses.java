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
package com.palantir.dialogue.core;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.QosReason.DueTo;
import com.palantir.conjure.java.api.errors.QosReason.RetryHint;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.util.Optional;
import java.util.OptionalDouble;
import org.jspecify.annotations.Nullable;

/** Utility functionality for {@link Response} handling. */
final class Responses {
    private static final SafeLogger log = SafeLoggerFactory.get(Responses.class);

    @VisibleForTesting
    static final String PROXY_UPSTREAM_REQUEST_ATTEMPTS = "Proxy-Upstream-Request-Attempts";

    @VisibleForTesting
    static final String UTILIZATION_HEADER = "X-Witchcraft-Utilization";

    static boolean isRetryOther(@Nullable Response response) {
        // Note that a 308 status may be a non-retryable signal, for instance google sometimes
        // uses a '308 Resume Incomplete', so we must verify the presence of a Location header
        // to differentiate the two.
        return response != null
                && response.code() == 308
                && response.getFirstHeader(HttpHeaders.LOCATION).isPresent();
    }

    static boolean isTooManyRequests(@Nullable Response response) {
        return response != null && response.code() == 429;
    }

    static boolean isUnavailable(@Nullable Response response) {
        return response != null && response.code() == 503;
    }

    static boolean isQosStatus(@Nullable Response response) {
        return isRetryOther(response) || isTooManyRequests(response) || isUnavailable(response);
    }

    static boolean isQosDueToCustom(@Nullable Response result) {
        if (result == null || !isQosStatus(result)) {
            return false;
        }
        QosReason reason = DialogueQosReasonDecoder.parse(result);
        return reason.dueTo().isPresent() && DueTo.CUSTOM.equals(reason.dueTo().get());
    }

    static boolean isRetryableQos(Response result) {
        if (!isQosStatus(result)) {
            return false;
        }
        QosReason reason = DialogueQosReasonDecoder.parse(result);
        return reason.retryHint().isEmpty()
                || !RetryHint.DO_NOT_RETRY.equals(reason.retryHint().get());
    }

    static boolean isServerErrorRange(@Nullable Response response) {
        return response == null || response.code() / 100 == 5;
    }

    static boolean isInternalServerError(@Nullable Response response) {
        return response == null || response.code() == 500;
    }

    static boolean isSuccess(@Nullable Response response) {
        return response != null && response.code() / 100 == 2;
    }

    static boolean isClientError(@Nullable Response response) {
        return response != null && response.code() / 100 == 4;
    }

    /**
     * Returns the integer value of the {@link #PROXY_UPSTREAM_REQUEST_ATTEMPTS} {@link Response#headers() header}.
     * When the value is missing, negative, or otherwise cannot be parsed, zero is returned.
     * Rejecting negative values is critical because clients mustn't roll back retry counters based on
     * responses from a remote server.
     */
    static int getProxyUpstreamRequestAttempts(Response response) {
        Optional<String> maybeProxyUpstreamRequestAttempts = response.getFirstHeader(PROXY_UPSTREAM_REQUEST_ATTEMPTS);
        if (maybeProxyUpstreamRequestAttempts.isPresent()) {
            String proxyUpstreamRequestAttempts = maybeProxyUpstreamRequestAttempts.get();
            try {
                int parsed = Integer.parseInt(proxyUpstreamRequestAttempts.trim());
                if (parsed >= 0) {
                    return parsed;
                }
                log.warn(
                        "Received an unexpected negative proxy upstream request attempts value, using zero",
                        SafeArg.of("proxyUpstreamRequestAttempts", proxyUpstreamRequestAttempts));
            } catch (NumberFormatException e) {
                log.warn(
                        "Failed to parse proxy upstream request attempts, assuming zero",
                        SafeArg.of("proxyUpstreamRequestAttempts", proxyUpstreamRequestAttempts),
                        e);
            }
        }
        return 0;
    }

    /**
     * Reads the server-reported utilization from the {@value #UTILIZATION_HEADER} {@link Response#headers() header}:
     * a decimal in {@code [0, 1]} where higher means more loaded, computed by the server (see witchcraft's
     * {@code UtilizationHandler}). Returns empty when the header is missing, unparseable, negative, or non-finite;
     * parsing is defensive because a bad value from one node must not disrupt node selection.
     */
    static OptionalDouble parseUtilization(Response response) {
        Optional<String> maybeHeader = response.getFirstHeader(UTILIZATION_HEADER);
        if (maybeHeader.isEmpty()) {
            return OptionalDouble.empty();
        }
        String value = maybeHeader.get();
        double utilization;
        try {
            utilization = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse utilization header, ignoring", SafeArg.of("utilization", value), e);
            return OptionalDouble.empty();
        }
        if (!Double.isFinite(utilization) || utilization < 0) {
            log.warn("Received an unexpected utilization value, ignoring", SafeArg.of("utilization", value));
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(utilization);
    }

    private Responses() {}
}
