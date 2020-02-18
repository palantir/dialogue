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

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.codahale.metrics.Timer;
import com.palantir.tritium.metrics.registry.AbstractTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class TaggedMetrics extends AbstractTaggedMetricRegistry {
    private final Clock clock;

    public TaggedMetrics(Clock clock) {
        super(() -> new SlidingTimeWindowArrayReservoir(1, TimeUnit.DAYS, clock));
        this.clock = clock;
    }

    @Override
    protected Supplier<Meter> meterSupplier() {
        return () -> new Meter(clock);
    }

    @Override
    protected Supplier<Timer> timerSupplier() {
        return () -> new Timer(createReservoir(), clock);
    }

    /** Helper method to create an untagged metric. */
    public Meter meter(String name) {
        return meter(MetricName.builder().safeName(name).build());
    }

    /** Helper method to create an untagged metric. */
    public Counter counter(String name) {
        return counter(MetricName.builder().safeName(name).build());
    }

    // TODO(dfox): disable methods involving MetricSets somehow, as they don't use our clock
}
