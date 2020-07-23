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

import com.codahale.metrics.Gauge;
import com.google.common.annotations.VisibleForTesting;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.LongStream;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An internally-mutable gauge which computes an integer value by applying a gaugeFunction to 0 or more
 * source elements stored in a WeakHashMap. When source elements are GC'd, they will no longer be represented in
 * the final summary integer.
 *
 * Not intended for public usage, but needed in multiple packages.
 */
@ThreadSafe
public final class DialogueInternalWeakReducingGauge<T> implements Gauge<Number> {

    @GuardedBy("this")
    private final Set<T> weakSet = Collections.newSetFromMap(new WeakHashMap<>(2));

    private final ToLongFunction<T> gaugeFunction;
    private final Function<LongStream, Number> operator;

    @VisibleForTesting
    DialogueInternalWeakReducingGauge(ToLongFunction<T> gaugeFunction, Function<LongStream, Number> reduceFunction) {
        this.gaugeFunction = gaugeFunction;
        this.operator = reduceFunction;
    }

    /** Register a new source element which will be used to compute the future summary integer. */
    public synchronized void add(T sourceElement) {
        weakSet.add(sourceElement);
    }

    @Override
    public synchronized Number getValue() {
        return operator.apply(weakSet.stream().mapToLong(gaugeFunction));
    }

    public static <T> void register(
            TaggedMetricRegistry taggedMetricRegistry,
            MetricName metricName,
            ToLongFunction<T> toLongFunc,
            Function<LongStream, Number> reducingFunction,
            T initialObject) {

        DialogueInternalWeakReducingGauge<T> freshInstance =
                new DialogueInternalWeakReducingGauge<>(toLongFunc, reducingFunction);

        // intentionally using 'gauge' not 'registerWithReplacement' because we want to access the existing one.
        Gauge<Number> fromRegistry = taggedMetricRegistry.gauge(metricName, freshInstance);

        // anyone can implemented TaggedMetricRegistry, and there's no guarantee we get the same thing out that we
        // put in. If this happens, the fancy 'summarising across many instances' behaviour probably won't work.
        if (!(fromRegistry instanceof DialogueInternalWeakReducingGauge)) {
            freshInstance.add(initialObject);
            return;
        }

        DialogueInternalWeakReducingGauge<T> gauge = (DialogueInternalWeakReducingGauge<T>) fromRegistry;
        gauge.add(initialObject);
    }
}
