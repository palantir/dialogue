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

/** Utility functionality for {@link Response} handling. */
final class Responses {
    private static final SafeLogger log = SafeLoggerFactory.get(Responses.class);

    @VisibleForTesting
    static final String PROXY_UPSTREAM_REQUEST_ATTEMPTS = "Proxy-Upstream-Request-Attempts";

    static boolean isRetryOther(Response response) {
        // Note that a 308 status may be a non-retryable signal, for instance google sometimes
        // uses a '308 Resume Incomplete', so we must verify the presence of a Location header
        // to differentiate the two.
        return response.code() == 308
                && response.getFirstHeader(HttpHeaders.LOCATION).isPresent();
    }

    static boolean isTooManyRequests(Response response) {
        return response.code() == 429;
    }

    static boolean isUnavailable(Response response) {
        return response.code() == 503;
    }

    static boolean isQosStatus(Response response) {
        return isRetryOther(response) || isTooManyRequests(response) || isUnavailable(response);
    }

    static boolean isQosDueToCustom(Response result) {
        if (!isQosStatus(result)) {
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

    static boolean isServerErrorRange(Response response) {
        return response.code() / 100 == 5;
    }

    static boolean isInternalServerError(Response response) {
        return response.code() == 500;
    }

    static boolean isSuccess(Response response) {
        return response.code() / 100 == 2;
    }

    static boolean isClientError(Response response) {
        return response.code() / 100 == 4;
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
            } catch (NumberFormatException e) {
                log.warn(
                        "Failed to parse proxy upstream request attempts, assuming zero",
                        SafeArg.of("proxyUpstreamRequestAttempts", proxyUpstreamRequestAttempts),
                        e);
            }
        }
        return 0;
    }

    private Responses() {}
}
