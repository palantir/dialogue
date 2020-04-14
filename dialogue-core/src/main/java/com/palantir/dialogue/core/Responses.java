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

import com.palantir.dialogue.Response;

/** Utility functionality for {@link Response} handling. */
final class Responses {

    private static boolean isRetryOther(Response response) {
        return response.code() == 308;
    }

    private static boolean isTooManyRequests(Response response) {
        return response.code() == 429;
    }

    private static boolean isUnavailable(Response response) {
        return response.code() == 503;
    }

    static boolean isQosStatus(Response response) {
        return isRetryOther(response) || isTooManyRequests(response) || isUnavailable(response);
    }

    static boolean isServerError(Response response) {
        return response.code() / 100 == 5;
    }

    private Responses() {}
}
