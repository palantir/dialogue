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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.palantir.dialogue.Endpoint;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;

final class MetricNames {
    /** Counter incremented every time a {@code Response} body is closed. */
    static Counter bodyClose(TaggedMetricRegistry reg) {
        return reg.counter(MetricName.builder().safeName("bodyClose").build());
    }

    /** Counter for how many responses are issued across all servers. */
    static Counter globalResponses(TaggedMetricRegistry registry) {
        return registry.counter(MetricName.builder().safeName("globalResponses").build());
    }

    /** Counter for how long servers spend processing requests. */
    static Counter globalServerTimeNanos(TaggedMetricRegistry registry) {
        return registry.counter(
                MetricName.builder().safeName("globalServerTime").build());
    }

    static Counter activeRequests(TaggedMetricRegistry reg, String serverName) {
        return reg.counter(MetricName.builder()
                .safeName("activeRequests")
                .putSafeTags("server", serverName)
                .build());
    }

    /** Marked every time a server received a request. */
    static Meter requestMeter(TaggedMetricRegistry reg, String serverName, Endpoint endpoint) {
        return reg.meter(MetricName.builder()
                .safeName("request")
                .putSafeTags("server", serverName)
                .putSafeTags("endpoint", endpoint.endpointName())
                .build());
    }

    private MetricNames() {}
}
