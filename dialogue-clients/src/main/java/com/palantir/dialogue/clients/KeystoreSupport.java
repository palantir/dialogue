/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.dialogue.core.DialogueExecutors;
import com.palantir.dialogue.core.SslStoreMetadata;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class KeystoreSupport {

    private static final SafeLogger log = SafeLoggerFactory.get(KeystoreSupport.class);
    private static final String SCHEDULER_NAME = "dialogue-client-ssl-store-scheduler";
    private static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(5);

    private static final Cleaner cleaner = Cleaner.create(new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("dialogue-client-ssl-store-cleaner-%d")
            .build());

    @SuppressWarnings("deprecation") // Singleton registry for a singleton executor
    private static final Supplier<ScheduledExecutorService> sharedScheduler =
            Suppliers.memoize(() -> DialogueExecutors.newSharedSingleThreadScheduler(MetricRegistries.instrument(
                    SharedTaggedMetricRegistries.getSingleton(),
                    new ThreadFactoryBuilder()
                            .setNameFormat(SCHEDULER_NAME + "-%d")
                            .setDaemon(true)
                            .build(),
                    SCHEDULER_NAME)));

    static Refreshable<SslStoreMetadata> pollForChanges(SslConfiguration sslConfiguration) {
        return pollForChanges(sslConfiguration, sharedScheduler.get(), DEFAULT_REFRESH_INTERVAL);
    }

    @VisibleForTesting
    static Refreshable<SslStoreMetadata> pollForChanges(
            SslConfiguration sslConfiguration, ScheduledExecutorService executor, Duration refreshInterval) {

        SettableRefreshable<SslStoreMetadata> metadataRefreshable =
                Refreshable.create(SslStoreMetadata.of(sslConfiguration));

        MetadataPollingTask task = new MetadataPollingTask(sslConfiguration, metadataRefreshable);
        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                task, refreshInterval.toMillis(), refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
        cleaner.register(metadataRefreshable, new CleanupTask(future));
        return metadataRefreshable;
    }

    private KeystoreSupport() {}

    private static final class MetadataPollingTask implements Runnable {

        private final SslConfiguration sslConfiguration;
        private final WeakReference<SettableRefreshable<SslStoreMetadata>> metadataRefreshable;

        private MetadataPollingTask(
                SslConfiguration sslConfiguration, SettableRefreshable<SslStoreMetadata> metadataRefreshable) {
            this.sslConfiguration = sslConfiguration;
            this.metadataRefreshable = new WeakReference<>(metadataRefreshable);
        }

        @Override
        public void run() {
            SettableRefreshable<SslStoreMetadata> refreshable = metadataRefreshable.get();
            if (refreshable == null) {
                log.info("Output refreshable has been garbage collected, no need to continue polling");
                return;
            }
            try {
                // TODO(#100): Add in cheaper comparison
                SslStoreMetadata updated = SslStoreMetadata.of(sslConfiguration);
                if (!updated.equals(refreshable.get())) {
                    refreshable.update(updated);
                }
            } catch (RuntimeException e) {
                log.error("Failed to refresh ssl store metadata", e);
            }
        }
    }

    private static final class CleanupTask implements Runnable {

        private static final SafeLogger log = SafeLoggerFactory.get(CleanupTask.class);

        private final AtomicBoolean executed = new AtomicBoolean();
        private final ScheduledFuture<?> scheduledFuture;

        private CleanupTask(ScheduledFuture<?> scheduledFuture) {
            this.scheduledFuture = scheduledFuture;
        }

        @Override
        public void run() {
            if (!executed.getAndSet(true)) {
                log.debug("Unregistering ssl metadata background worker");
                scheduledFuture.cancel(false);
            }
        }
    }
}
