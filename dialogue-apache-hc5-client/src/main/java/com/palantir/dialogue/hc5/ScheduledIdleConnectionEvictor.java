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

import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.dialogue.core.DialogueExecutors;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.hc.core5.pool.ConnPoolControl;

/**
 * Connection evictor based on the hc5 IdleConnectionEvictor, but using a scheduled executor instead of
 * a new thread for each pool.
 */
final class ScheduledIdleConnectionEvictor {
    private static final SafeLogger log = SafeLoggerFactory.get(ScheduledIdleConnectionEvictor.class);
    private static final String EXECUTOR_NAME = "DialogueIdleConnectionEvictor";
    /*
     * Shared single thread executor is reused between idle connection evictors.
     */
    @SuppressWarnings("deprecation") // Singleton registry for a singleton executor
    private static final Supplier<ScheduledExecutorService> sharedScheduler =
            Suppliers.memoize(() -> MetricRegistries.instrument(
                    SharedTaggedMetricRegistries.getSingleton(),
                    DialogueExecutors.newSharedSingleThreadScheduler(MetricRegistries.instrument(
                            SharedTaggedMetricRegistries.getSingleton(),
                            new ThreadFactoryBuilder()
                                    .setNameFormat(EXECUTOR_NAME + "-%d")
                                    .setDaemon(true)
                                    .build(),
                            EXECUTOR_NAME)),
                    EXECUTOR_NAME));

    static ScheduledFuture<?> schedule(ConnPoolControl<?> connectionManager, Duration delayBetweenChecks) {
        return schedule(connectionManager, delayBetweenChecks, sharedScheduler.get());
    }

    private static ScheduledFuture<?> schedule(
            ConnPoolControl<?> connectionManager, Duration delayBetweenChecks, ScheduledExecutorService scheduler) {
        return scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        connectionManager.closeExpired();
                    } catch (RuntimeException | Error e) {
                        log.warn("Exception caught while evicting idle connections", e);
                    }
                },
                delayBetweenChecks.toMillis(),
                delayBetweenChecks.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private ScheduledIdleConnectionEvictor() {}
}
