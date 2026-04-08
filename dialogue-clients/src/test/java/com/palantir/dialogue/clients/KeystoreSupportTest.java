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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.core.SslStoreMetadata;
import com.palantir.refreshable.Refreshable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeystoreSupportTest {
    @Test
    void pollForChanges_updates_on_change(@TempDir Path tempDir) throws IOException {
        Path trustStore = tempDir.resolve("trustStore.jks");
        Path keyStore = tempDir.resolve("keyStore.jks");
        Files.copy(TestConfigurations.SSL_CONFIG.trustStorePath(), trustStore);
        Files.copy(TestConfigurations.SSL_CONFIG.keyStorePath().orElseThrow(), keyStore);

        SslConfiguration sslConfiguration = SslConfiguration.of(trustStore, keyStore, "keystore");
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        try {
            Refreshable<SslStoreMetadata> refreshable =
                    SslStoresSupport.pollForChanges(sslConfiguration, executorService, Duration.ofMillis(25));

            SslStoreMetadata initial = refreshable.get();
            HashCode initialTrustHash = initial.trustStore().hash();
            HashCode initialKeyHash = initial.keyStore().hash();

            // Corrupts the truststore for the sake of testing updates
            Files.write(trustStore, new byte[] {1, 0, 0, 0}, StandardOpenOption.APPEND);
            HashCode updatedTrustHash = Hashing.murmur3_128().hashBytes(Files.readAllBytes(trustStore));

            Awaitility.waitAtMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                SslStoreMetadata updated = refreshable.get();
                assertThat(updated).isNotEqualTo(initial);
                assertThat(updated.trustStore().hash()).isNotEqualTo(initialTrustHash);
                assertThat(updated.trustStore().hash()).isEqualTo(updatedTrustHash);
                assertThat(updated.keyStore().hash()).isEqualTo(initialKeyHash);
            });

            SslStoreMetadata updatedStoreMetadata = refreshable.get();

            Files.write(keyStore, new byte[] {2, 0, 0, 0}, StandardOpenOption.APPEND);
            HashCode updatedKeyHash = Hashing.murmur3_128().hashBytes(Files.readAllBytes(keyStore));

            Awaitility.waitAtMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                SslStoreMetadata updated = refreshable.get();
                assertThat(updated).isNotEqualTo(updatedStoreMetadata);
                assertThat(updated.trustStore().hash())
                        .isEqualTo(updatedStoreMetadata.trustStore().hash());
                assertThat(updated.keyStore().hash()).isEqualTo(updatedKeyHash);
                assertThat(updated.keyStore().hash()).isNotEqualTo(initialKeyHash);
            });
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, 5, TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    @Test
    void pollForChanges_handles_read_failures(@TempDir Path tempDir) throws Exception {
        Path trustStore = tempDir.resolve("trustStore.jks");
        Path keyStore = tempDir.resolve("keyStore.jks");
        Path movedTrustStore = tempDir.resolve("trustStore.moved.jks");
        Files.copy(TestConfigurations.SSL_CONFIG.trustStorePath(), trustStore);
        Files.copy(TestConfigurations.SSL_CONFIG.keyStorePath().orElseThrow(), keyStore);

        SslConfiguration sslConfiguration = SslConfiguration.of(trustStore, keyStore, "keystore");
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        try {
            Refreshable<SslStoreMetadata> refreshable =
                    SslStoresSupport.pollForChanges(sslConfiguration, executorService, Duration.ofMillis(25));
            HashCode initialTrustHash = refreshable.get().trustStore().hash();

            Files.move(trustStore, movedTrustStore);
            Thread.sleep(150);

            Files.move(movedTrustStore, trustStore);
            Files.write(trustStore, new byte[] {9}, StandardOpenOption.APPEND);

            Awaitility.waitAtMost(Duration.ofSeconds(10))
                    .untilAsserted(() ->
                            assertThat(refreshable.get().trustStore().hash()).isNotEqualTo(initialTrustHash));
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, 5, TimeUnit.SECONDS))
                    .isTrue();
        }
    }
}
