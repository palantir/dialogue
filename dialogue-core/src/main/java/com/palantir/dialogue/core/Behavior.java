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
            if (Responses.isTooManyRequests(result)
                    || Responses.isInternalServerError(result)
                    || Responses.isQosDueToCustom(result)) {
                // 429, 500, or QoS due to a custom reason
                control.ignore();
            } else if ((Responses.isQosStatus(result) && !Responses.isTooManyRequests(result))
                    || Responses.isServerErrorRange(result)) {
                // 308 with Location header, or 501-599
                control.dropped();
            } else {
                control.success();
            }
        }

        @Override
        void onFailure(Throwable throwable, PermitControl control) {
            if (throwable instanceof IOException) {
                control.dropped();
            } else {
                control.ignore();
            }
        }
    },
    ENDPOINT_LEVEL() {
        @Override
        void onSuccess(Response result, PermitControl control) {
            if ((Responses.isTooManyRequests(result) && !Responses.isQosDueToCustom(result))
                    || Responses.isInternalServerError(result)) {
                // non-custom 429 or 500
                control.dropped();
            } else if (Responses.isServerErrorRange(result)) {
                // 501-599
                control.ignore();
            } else {
                control.success();
            }
        }

        @Override
        void onFailure(Throwable _throwable, PermitControl control) {
            control.ignore();
        }
    },
    STICKY() {
        @Override
        void onSuccess(Response _result, PermitControl control) {
            control.success();
        }

        @Override
        void onFailure(Throwable _throwable, PermitControl control) {
            control.ignore();
        }
    };

    abstract void onSuccess(Response result, PermitControl control);

    abstract void onFailure(Throwable throwable, PermitControl control);

    /** The limiter-side hooks a {@link Behavior} invokes to record the effect of a completed request. */
    interface PermitControl {

        /**
         * Indicates that the effect of the request corresponding to this permit on concurrency limits should be
         * ignored.
         */
        void ignore();

        /**
         * Indicates that the request corresponding to this permit was dropped and that the concurrency limit should be
         * multiplicatively decreased.
         */
        void dropped();

        /**
         * Indicates that the request corresponding to this permit was successful and that the concurrency limit should
         * be increased.
         */
        void success();
    }
}
