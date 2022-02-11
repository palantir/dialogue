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

package com.palantir.dialogue.hc5;

import com.codahale.metrics.Counter;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;

final class UnknownHostCounter {
    private final String clientName;
    private final Counter counter;

    static UnknownHostCounter of(String clientName, TaggedMetricRegistry metrics) {
        return new UnknownHostCounter(clientName, DialogueClientMetrics.of(metrics));
    }

    UnknownHostCounter(String clientName, DialogueClientMetrics metrics) {
        this.clientName = clientName;
        this.counter = metrics.connectionResolutionError(clientName);
    }

    @Override
    public String toString() {
        return "UnknownHostDetector{clientName='" + clientName + '}';
    }

    public void reportUnknownHostException() {
        this.counter.inc();
    }
}
