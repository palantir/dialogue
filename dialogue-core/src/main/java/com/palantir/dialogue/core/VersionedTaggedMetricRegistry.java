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
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class VersionedTaggedMetricRegistry implements TaggedMetricRegistry {
    private static final String DIALOGUE_VERSION = Optional.ofNullable(
                    VersionedTaggedMetricRegistry.class.getPackage().getImplementationVersion())
            .orElse("dev");

    private final TaggedMetricRegistry delegate;

    private VersionedTaggedMetricRegistry(TaggedMetricRegistry delegate) {
        this.delegate = delegate;
    }

    static VersionedTaggedMetricRegistry create(TaggedMetricRegistry delegate) {
        if (delegate instanceof VersionedTaggedMetricRegistry) {
            return (VersionedTaggedMetricRegistry) delegate;
        }
        return new VersionedTaggedMetricRegistry(delegate);
    }

    private MetricName augment(MetricName name) {
        return MetricName.builder()
                .from(name)
                .putSafeTags("dialogueVersion", DIALOGUE_VERSION)
                .build();
    }

    @Override
    public Map<MetricName, Metric> getMetrics() {
        return delegate.getMetrics();
    }

    @Override
    public void forEachMetric(BiConsumer<MetricName, Metric> consumer) {
        delegate.forEachMetric(consumer);
    }

    @Override
    public <T> Optional<Gauge<T>> gauge(MetricName metricName) {
        return delegate.gauge(augment(metricName));
    }

    @Override
    public <T> Gauge<T> gauge(MetricName metricName, Gauge<T> gauge) {
        return delegate.gauge(augment(metricName), gauge);
    }

    @Override
    public void registerWithReplacement(MetricName metricName, Gauge<?> gauge) {
        delegate.registerWithReplacement(augment(metricName), gauge);
    }

    @Override
    public Timer timer(MetricName metricName) {
        return delegate.timer(augment(metricName));
    }

    @Override
    public Timer timer(MetricName metricName, Supplier<Timer> timerSupplier) {
        return delegate.timer(augment(metricName), timerSupplier);
    }

    @Override
    public Meter meter(MetricName metricName) {
        return delegate.meter(augment(metricName));
    }

    @Override
    public Meter meter(MetricName metricName, Supplier<Meter> meterSupplier) {
        return delegate.meter(augment(metricName), meterSupplier);
    }

    @Override
    public Histogram histogram(MetricName metricName) {
        return delegate.histogram(augment(metricName));
    }

    @Override
    public Histogram histogram(MetricName metricName, Supplier<Histogram> histogramSupplier) {
        return delegate.histogram(augment(metricName), histogramSupplier);
    }

    @Override
    public Counter counter(MetricName metricName) {
        return delegate.counter(augment(metricName));
    }

    @Override
    public Counter counter(MetricName metricName, Supplier<Counter> counterSupplier) {
        return delegate.counter(augment(metricName), counterSupplier);
    }

    @Override
    public void addMetrics(String _safeTagName, String _safeTagValue, TaggedMetricSet _metrics) {
        throw new UnsupportedOperationException(
                "Operations involving transforming metricsets are not implemented as we don't need them in dialogue");
    }

    @Override
    public Optional<Metric> remove(MetricName _metricName) {
        throw new UnsupportedOperationException(
                "Removal operations are not implemented as we don't need them in dialogue");
    }

    @Override
    public Optional<TaggedMetricSet> removeMetrics(String _safeTagName, String _safeTagValue) {
        throw new UnsupportedOperationException(
                "Removal operations are not implemented as we don't need them in dialogue");
    }

    @Override
    public boolean removeMetrics(String _safeTagName, String _safeTagValue, TaggedMetricSet _metrics) {
        throw new UnsupportedOperationException(
                "Removal operations are not implemented as we don't need them in dialogue");
    }
}
