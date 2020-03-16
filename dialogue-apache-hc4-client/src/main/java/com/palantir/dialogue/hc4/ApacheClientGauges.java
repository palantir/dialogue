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

import com.codahale.metrics.Metric;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricSet;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * Utility to allow gauges to be associated with the apache client {@link PoolingHttpClientConnectionManager}
 * safely without replacing existing values. This is important when clients are refreshed, or dialogue clients
 * are built outside of standard frameworks.
 */
@SuppressWarnings("UnnecessaryLambda")
final class ApacheClientGauges {

    private static final ConcurrentMap<String, List<WeakReference<PoolingHttpClientConnectionManager>>> poolsByName =
            new ConcurrentHashMap<>();
    private static final TaggedMetricSet metricSet = new TaggedMetricSet() {
        @Override
        public Map<MetricName, Metric> getMetrics() {
            ImmutableMap.Builder<MetricName, Metric> results = ImmutableMap.builder();
            forEachMetric(results::put);
            return results.build();
        }

        @Override
        public void forEachMetric(BiConsumer<MetricName, Metric> consumer) {
            TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
            DialogueClientPoolMetrics poolMetrics = DialogueClientPoolMetrics.of(registry);
            poolsByName.forEach((channelName, pools) -> {
                poolMetrics
                        .size()
                        .channelName(channelName)
                        .state("idle")
                        .build(() -> collect(
                                pools, pool -> (long) pool.getTotalStats().getAvailable(), Long::sum, 0L));
                poolMetrics
                        .size()
                        .channelName(channelName)
                        .state("leased")
                        .build(() -> collect(
                                pools, pool -> (long) pool.getTotalStats().getLeased(), Long::sum, 0L));
                poolMetrics
                        .size()
                        .channelName(channelName)
                        .state("pending")
                        .build(() -> collect(
                                pools, pool -> (long) pool.getTotalStats().getPending(), Long::sum, 0L));
            });
            registry.forEachMetric(consumer);
        }
    };

    static void register(String channelName, PoolingHttpClientConnectionManager connectionPool) {
        List<WeakReference<PoolingHttpClientConnectionManager>> pools =
                poolsByName.computeIfAbsent(channelName, ignored -> new CopyOnWriteArrayList<>());
        pools.add(new WeakReference<>(connectionPool));
        pools.removeIf(item -> item.get() == null);
    }

    static void install(TaggedMetricRegistry registry) {
        registry.addMetrics("set", "apacheClientGauges", metricSet);
    }

    private static <T> T collect(
            List<WeakReference<PoolingHttpClientConnectionManager>> pools,
            Function<PoolingHttpClientConnectionManager, T> extractor,
            BiFunction<T, T, T> combiner,
            T initial) {
        T current = initial;
        Iterator<WeakReference<PoolingHttpClientConnectionManager>> iterator = pools.iterator();
        while (iterator.hasNext()) {
            PoolingHttpClientConnectionManager pool = iterator.next().get();
            if (pool != null) {
                T poolValue = extractor.apply(pool);
                current = combiner.apply(current, poolValue);
            } else {
                // The connection pool has been garbage collected
                iterator.remove();
            }
        }
        return current;
    }

    /** Clears all known connection pools. Exists <i>only</i> for testing, this should never be used otherwise. */
    @VisibleForTesting
    static void resetForTesting() {
        poolsByName.clear();
    }

    private ApacheClientGauges() {}
}
