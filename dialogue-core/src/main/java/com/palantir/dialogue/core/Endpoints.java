/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;

/** Utility functionality for {@link com.palantir.dialogue.Endpoint} handling. **/
final class Endpoints {

    private Endpoints() {}

    /**
     * We are a bit more conservative than the definition of Safe and Idempotent in https://tools.ietf
     * .org/html/rfc7231#section-4.2.1, as we're not sure whether developers have written non-idempotent PUT/DELETE
     * endpoints.
     */
    static boolean safeToRetry(Endpoint endpoint) {
        HttpMethod httpMethod = endpoint.httpMethod();
        switch (httpMethod) {
            case GET:
            case HEAD:
            case OPTIONS:
                return true;
            case PUT:
            case DELETE:
                // in theory PUT and DELETE should be fine to retry too, we're just being conservative for now.
            case POST:
            case PATCH:
                return false;
        }

        throw new SafeIllegalStateException("Unknown method", SafeArg.of("httpMethod", httpMethod));
    }
}
