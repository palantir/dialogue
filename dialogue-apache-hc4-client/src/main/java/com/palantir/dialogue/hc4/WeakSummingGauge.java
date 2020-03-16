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

package com.palantir.dialogue.hc4;

import com.codahale.metrics.Gauge;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.ToIntFunction;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An internally-mutable gauge which computes an integer value by applying a gaugeFunction to 0 or more
 * source elements stored in a WeakHashMap. When source elements are GC'd, they will no longer be represented in
 * the final summary integer.
 */
@ThreadSafe
final class WeakSummingGauge<T> implements Gauge<Integer> {
    private final ToIntFunction<T> gaugeFunction;
    private final Set<T> weakSet = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>(2)));

    WeakSummingGauge(ToIntFunction<T> gaugeFunction) {
        this.gaugeFunction = gaugeFunction;
    }

    /** Register a new source element which will be used to compute the future summary integer. */
    public void add(T sourceElement) {
        weakSet.add(sourceElement);
    }

    @Override
    public Integer getValue() {
        return weakSet.stream().mapToInt(gaugeFunction).sum();
    }

    public static <T> WeakSummingGauge<T> getOrCreate(
            ToIntFunction<T> toIntFunction,
            T initialValue,
            TaggedMetricRegistry taggedMetricRegistry,
            MetricName metricName) {
        // intentionally using 'gauge' not 'registerWithReplacement' because we want to access the existing one.
        WeakSummingGauge<T> gauge =
                (WeakSummingGauge<T>) taggedMetricRegistry.gauge(metricName, new WeakSummingGauge<>(toIntFunction));
        gauge.add(initialValue);
        return gauge;
    }
}
