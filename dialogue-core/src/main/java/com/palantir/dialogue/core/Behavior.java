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

package com.palantir.dialogue.core;

import com.palantir.dialogue.Response;
import java.io.IOException;

enum Behavior {
    HOST_LEVEL() {
        @Override
        void onSuccess(Response result, PermitControl control) {
            // 429, 500, and QoS due to a custom reason are ignored, other QoS responses and 501-599 are dropped
            if (Responses.isQosDueToCustom(result)) {
                control.ignore(QOS_CUSTOM);
            } else if (Responses.isTooManyRequests(result)) {
                control.ignore(HTTP_429);
            } else if (Responses.isInternalServerError(result)) {
                control.ignore(HTTP_500);
            } else if (Responses.isRetryOther(result)) {
                // 308 with Location header
                control.dropped(HTTP_308);
            } else if (Responses.isUnavailable(result)) {
                control.dropped(HTTP_503);
            } else if (Responses.isServerErrorRange(result)) {
                control.dropped(HTTP_5XX);
            } else {
                control.success(statusFamily(result));
            }
        }

        @Override
        void onFailure(Throwable throwable, PermitControl control) {
            if (throwable instanceof IOException) {
                control.dropped(failureCause(throwable));
            } else {
                control.ignore(failureCause(throwable));
            }
        }
    },
    ENDPOINT_LEVEL() {
        @Override
        void onSuccess(Response result, PermitControl control) {
            if (Responses.isTooManyRequests(result) && !Responses.isQosDueToCustom(result)) {
                // non-custom 429
                control.dropped(HTTP_429);
            } else if (Responses.isInternalServerError(result)) {
                control.dropped(HTTP_500);
            } else if (Responses.isServerErrorRange(result)) {
                // 501-599
                control.ignore(statusFamily(result));
            } else {
                control.success(statusFamily(result));
            }
        }

        @Override
        void onFailure(Throwable throwable, PermitControl control) {
            control.ignore(failureCause(throwable));
        }
    },
    STICKY() {
        @Override
        void onSuccess(Response result, PermitControl control) {
            control.success(statusFamily(result));
        }

        @Override
        void onFailure(Throwable throwable, PermitControl control) {
            control.ignore(failureCause(throwable));
        }
    };

    private static final String HTTP_2XX = "http-2xx";
    private static final String HTTP_308 = "http-308";
    private static final String HTTP_4XX = "http-4xx";
    private static final String HTTP_429 = "http-429";
    private static final String HTTP_500 = "http-500";
    private static final String HTTP_503 = "http-503";
    private static final String HTTP_5XX = "http-5xx";
    private static final String QOS_CUSTOM = "qos-custom";

    abstract void onSuccess(Response result, PermitControl control);

    abstract void onFailure(Throwable throwable, PermitControl control);

    /** The cause for a response status that the branch did not identify more specifically. */
    private static String statusFamily(Response result) {
        if (Responses.isSuccess(result)) {
            return HTTP_2XX;
        }
        if (Responses.isClientError(result)) {
            return HTTP_4XX;
        }
        if (Responses.isServerErrorRange(result)) {
            return HTTP_5XX;
        }
        return ConcurrencyLimiterDecisionRecorder.OTHER_CAUSE;
    }

    /** Matches the exception tags used by {@code dialogue.client.request.retry} and the Apache client metrics. */
    private static String failureCause(Throwable throwable) {
        return throwable.getClass().getSimpleName();
    }

    /**
     * The limiter-side hooks a {@link Behavior} invokes to record the effect of a completed request. Each takes the
     * cause of the decision, describing the response status or failure, which is reported with it.
     */
    interface PermitControl {

        /**
         * Indicates that the effect of the request corresponding to this permit on concurrency limits should be
         * ignored.
         */
        void ignore(String cause);

        /**
         * Indicates that the request corresponding to this permit was dropped and that the concurrency limit should be
         * multiplicatively decreased.
         */
        void dropped(String cause);

        /**
         * Indicates that the request corresponding to this permit was successful, which increases the concurrency limit
         * only when enough requests were in flight.
         */
        void success(String cause);
    }
}
