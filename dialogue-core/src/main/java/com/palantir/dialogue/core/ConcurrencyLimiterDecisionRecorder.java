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

import com.codahale.metrics.Meter;
import com.palantir.dialogue.core.DialogueConcurrencylimiterMetrics.Decision_Result;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Records the {@code dialogue.concurrencylimiter.decision} meter, describing how a per-host concurrency limiter
 * interpreted each completed request.
 */
@FunctionalInterface
interface ConcurrencyLimiterDecisionRecorder {

    /** Discards decisions, for limiters that do not report them. */
    ConcurrencyLimiterDecisionRecorder NOOP = (_result, _cause) -> {};

    /** The cause of a decision that is not identified by a recognized response status or failure. */
    String OTHER_CAUSE = "other";

    void record(Decision_Result result, String cause);

    static ConcurrencyLimiterDecisionRecorder create(TaggedMetricRegistry registry, String channelName, int hostIndex) {
        return new Metered(DialogueConcurrencylimiterMetrics.of(registry), channelName, Integer.toString(hostIndex));
    }

    /** Decisions are recorded for every request, so meters are resolved lazily and cached. */
    final class Metered implements ConcurrencyLimiterDecisionRecorder {
        private final DialogueConcurrencylimiterMetrics metrics;
        private final String channelName;
        private final String hostIndex;

        // Only populated with the causes that occur, so unused combinations are never registered or reported.
        private final ConcurrentMap<String, Meter> successMeters = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Meter> droppedMeters = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Meter> ignoredMeters = new ConcurrentHashMap<>();

        private Metered(DialogueConcurrencylimiterMetrics metrics, String channelName, String hostIndex) {
            this.metrics = metrics;
            this.channelName = channelName;
            this.hostIndex = hostIndex;
        }

        @Override
        public void record(Decision_Result result, String cause) {
            ConcurrentMap<String, Meter> byCause = switch (result) {
                case SUCCESS -> successMeters;
                case DROPPED -> droppedMeters;
                case IGNORED -> ignoredMeters;
            };
            Meter meter = byCause.get(cause);
            if (meter == null) {
                // Racing threads resolve the same meter from the registry, so the last write wins harmlessly.
                meter = metrics.decision()
                        .channelName(channelName)
                        .hostIndex(hostIndex)
                        .result(result)
                        .cause(cause)
                        .build();
                byCause.put(cause, meter);
            }
            meter.mark();
        }
    }
}
