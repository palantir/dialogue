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

import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.tracing.Tracers;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/** Internal utility for singleton schedulers used by Dialogue. */
final class Schedulers {

    /*
     * Shared single thread executor is reused between all dialogue scheduled behavior. If it becomes oversaturated
     * we may wait longer than expected before resuming or timing out requests, but this is an
     * edge case where services are already operating in a degraded state and we should not
     * spam servers.
     */
    static final Supplier<ScheduledExecutorService> sharedScheduler =
            Suppliers.memoize(() -> Executors.newScheduledThreadPool(
                    // One scheduling thread is sufficient in most cases, but we allow another thread
                    // for every 16 cores in an effort to scale with available hardware.
                    Math.max(1, Runtime.getRuntime().availableProcessors() / 16),
                    new ThreadFactoryBuilder()
                            .setNameFormat("Dialogue-Scheduler-%d")
                            .setDaemon(false)
                            .build()));

    static ListeningScheduledExecutorService instrument(
            TaggedMetricRegistry metrics, ScheduledExecutorService scheduler, @CompileTimeConstant String name) {
        String schedulerName = "dialogue-" + name + "-scheduler";
        return MoreExecutors.listeningDecorator(
                Tracers.wrap(schedulerName, MetricRegistries.instrument(metrics, scheduler, schedulerName)));
    }

    private Schedulers() {}
}
