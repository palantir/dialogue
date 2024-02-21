/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.clients;

import com.codahale.metrics.Counter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.core.DialogueExecutors;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.Disposable;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.ref.Cleaner;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class DnsSupport {

    private static final String SCHEDULER_NAME = "dialogue-client-dns-scheduler";

    private static final Cleaner cleaner = Cleaner.create(new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("dialogue-client-dns-%d")
            .build());

    /*
     * Shared single thread executor is reused between all DNS polling components. If it becomes oversaturated
     * we may wait slightly longer than expected before refreshing DNS info, but this is an
     * edge case where services are already operating in a degraded state.
     */
    @SuppressWarnings("deprecation") // Singleton registry for a singleton executor
    private static final Supplier<ScheduledExecutorService> sharedScheduler =
            Suppliers.memoize(() -> DialogueExecutors.newSharedSingleThreadScheduler(MetricRegistries.instrument(
                    SharedTaggedMetricRegistries.getSingleton(),
                    new ThreadFactoryBuilder()
                            .setNameFormat(SCHEDULER_NAME + "-%d")
                            .setDaemon(true)
                            .build(),
                    SCHEDULER_NAME)));

    static Refreshable<ServicesConfigBlockWithResolvedHosts> pollForChanges(
            DialogueDnsResolver dnsResolver,
            Duration dnsRefreshInterval,
            TaggedMetricRegistry metrics,
            Refreshable<ServicesConfigBlock> input) {
        return pollForChanges(sharedScheduler.get(), dnsResolver, dnsRefreshInterval, metrics, input);
    }

    @VisibleForTesting
    static Refreshable<ServicesConfigBlockWithResolvedHosts> pollForChanges(
            ScheduledExecutorService executor,
            DialogueDnsResolver dnsResolver,
            Duration dnsRefreshInterval,
            TaggedMetricRegistry metrics,
            Refreshable<ServicesConfigBlock> input) {
        SettableRefreshable<ServicesConfigBlockWithResolvedHosts> dnsResolutionResult =
                Refreshable.create(ServicesConfigBlockWithResolvedHosts.empty());

        DialogueDnsResolutionWorker dnsResolutionWorker =
                new DialogueDnsResolutionWorker(dnsResolver, dnsResolutionResult);

        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                dnsResolutionWorker,
                dnsRefreshInterval.toMillis(),
                dnsRefreshInterval.toMillis(),
                TimeUnit.MILLISECONDS);
        Counter counter = ClientDnsMetrics.of(metrics).tasks();
        counter.inc();
        Disposable disposable = input.subscribe(dnsResolutionWorker::update);
        cleaner.register(dnsResolutionResult, new CleanupTask(disposable, future, counter));
        return dnsResolutionResult;
    }

    private DnsSupport() {}

    // We define a concrete class here to avoid accidental lambda references to the
    // cleaner target.
    private static final class CleanupTask implements Runnable {

        private static final SafeLogger log = SafeLoggerFactory.get(CleanupTask.class);

        private final AtomicBoolean executed = new AtomicBoolean();
        private final Disposable disposable;
        private final ScheduledFuture<?> scheduledFuture;
        private final Counter activeTasks;

        private CleanupTask(Disposable disposable, ScheduledFuture<?> scheduledFuture, Counter activeTasks) {
            this.disposable = disposable;
            this.scheduledFuture = scheduledFuture;
            this.activeTasks = activeTasks;
        }

        @Override
        public void run() {
            if (!executed.getAndSet(true)) {
                log.debug("Unregistering dns update background worker");
                disposable.dispose();
                scheduledFuture.cancel(false);
                activeTasks.dec();
            }
        }
    }
}