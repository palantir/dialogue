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

import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import javax.annotation.Nullable;
import org.immutables.value.Value;

interface Statistics {

    /** Returns a statisticId, to allow us to record info at the beginning and end of a request. */
    InFlightStage recordStart(Upstream upstream, Endpoint endpoint, Request request);

    interface InFlightStage {
        void recordComplete(@Nullable Response response, @Nullable Throwable throwable);
    }

    // TODO(dfox): allow recording more detailed statistics about the body upload  / download time?

    /**
     * Represents a connection to some upstream server. Should be OK to have multiple upstreams pointing
     * to one host if they have different cipher suites / proxy info / timeouts / protocol specs (h1/h2).
     */
    @Value.Immutable
    interface Upstream {
        @Value.Parameter
        String uri();
    }
}
