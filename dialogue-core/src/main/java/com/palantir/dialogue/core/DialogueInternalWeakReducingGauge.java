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
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.DoubleStream;
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

    private final Function<Set<T>, Number> function;

    @VisibleForTesting
    DialogueInternalWeakReducingGauge(ToLongFunction<T> gaugeFunction, Function<LongStream, Number> reduceFunction) {
        this(new LongGaugeProcessor<>(gaugeFunction, reduceFunction));
    }

    @VisibleForTesting
    DialogueInternalWeakReducingGauge(Function<Set<T>, Number> function) {
        this.function = function;
    }

    /** Register a new source element which will be used to compute the future summary integer. */
    public synchronized void add(T sourceElement) {
        weakSet.add(sourceElement);
    }

    @Override
    public synchronized Number getValue() {
        return function.apply(weakSet);
    }

    public static <T> DialogueInternalWeakReducingGauge<T> getOrCreate(
            TaggedMetricRegistry taggedMetricRegistry,
            MetricName metricName,
            ToLongFunction<T> toLongFunc,
            Function<LongStream, Number> reducingFunction,
            T initialObject) {
        // intentionally using 'gauge' not 'registerWithReplacement' because we want to access the existing one.
        DialogueInternalWeakReducingGauge<T> gauge = (DialogueInternalWeakReducingGauge<T>) taggedMetricRegistry.gauge(
                metricName,
                new DialogueInternalWeakReducingGauge<>(new LongGaugeProcessor<>(toLongFunc, reducingFunction)));
        gauge.add(initialObject);
        return gauge;
    }

    public static <T> DialogueInternalWeakReducingGauge<T> getOrCreateDouble(
            TaggedMetricRegistry taggedMetricRegistry,
            MetricName metricName,
            ToDoubleFunction<T> toLongFunc,
            Function<DoubleStream, Number> reducingFunction,
            T initialObject) {
        // intentionally using 'gauge' not 'registerWithReplacement' because we want to access the existing one.
        DialogueInternalWeakReducingGauge<T> gauge = (DialogueInternalWeakReducingGauge<T>) taggedMetricRegistry.gauge(
                metricName,
                new DialogueInternalWeakReducingGauge<>(new DoubleGaugeProcessor<>(toLongFunc, reducingFunction)));
        gauge.add(initialObject);
        return gauge;
    }

    private static final class LongGaugeProcessor<T> implements Function<Set<T>, Number> {

        private final ToLongFunction<T> function;
        private final Function<LongStream, Number> operator;

        LongGaugeProcessor(ToLongFunction<T> function, Function<LongStream, Number> operator) {
            this.function = function;
            this.operator = operator;
        }

        @Override
        public Number apply(Set<T> values) {
            return operator.apply(values.stream().mapToLong(function));
        }
    }

    private static final class DoubleGaugeProcessor<T> implements Function<Set<T>, Number> {

        private final ToDoubleFunction<T> function;
        private final Function<DoubleStream, Number> operator;

        DoubleGaugeProcessor(ToDoubleFunction<T> function, Function<DoubleStream, Number> operator) {
            this.function = function;
            this.operator = operator;
        }

        @Override
        public Number apply(Set<T> values) {
            return operator.apply(values.stream().mapToDouble(function));
        }
    }
}
