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
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private static final List<WeakReference<PoolingHttpClientConnectionManager>> pools = new CopyOnWriteArrayList<>();
    private static final Gauge<Long> idle =
            () -> collect(pool -> (long) pool.getTotalStats().getAvailable(), Long::sum, 0L);
    private static final Gauge<Long> leased =
            () -> collect(pool -> (long) pool.getTotalStats().getLeased(), Long::sum, 0L);
    private static final Gauge<Long> pending =
            () -> collect(pool -> (long) pool.getTotalStats().getPending(), Long::sum, 0L);

    static void register(PoolingHttpClientConnectionManager connectionPool) {
        pools.add(new WeakReference<>(connectionPool));
        pools.removeIf(item -> item.get() == null);
    }

    static void install(TaggedMetricRegistry registry) {
        DialogueClientPoolMetrics poolMetrics = DialogueClientPoolMetrics.of(registry);
        poolMetrics.size().state("idle").build(idle);
        poolMetrics.size().state("leased").build(leased);
        poolMetrics.size().state("pending").build(pending);
    }

    static <T> T collect(
            Function<PoolingHttpClientConnectionManager, T> extractor, BiFunction<T, T, T> combiner, T initial) {
        T current = initial;
        Iterator<WeakReference<PoolingHttpClientConnectionManager>> iterator = pools.iterator();
        while (iterator.hasNext()) {
            PoolingHttpClientConnectionManager pool = iterator.next().get();
            if (pool != null) {
                T poolValue = extractor.apply(pool);
                current = combiner.apply(current, poolValue);
            } else {
                iterator.remove();
            }
        }
        return current;
    }

    private ApacheClientGauges() {}
}
