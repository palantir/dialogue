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
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.core.SslStoreMetadata;
import com.palantir.refreshable.Refreshable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
        Refreshable<SslStoreMetadata> refreshable =
                KeystoreSupport.pollForChanges(sslConfiguration, executorService, Duration.ofMillis(25));

        SslStoreMetadata initial = refreshable.get();
        HashCode initialTrustHash = initial.trustStore().hash();
        long initialTrustSize = initial.trustStore().sizeBytes();

        // Corrupts the truststore for the sake of testing updates
        Files.write(trustStore, new byte[] {1, 0, 0, 0}, java.nio.file.StandardOpenOption.APPEND);

        Awaitility.waitAtMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            SslStoreMetadata updated = refreshable.get();
            assertThat(updated).isNotEqualTo(initial);
            assertThat(updated.trustStore().hash()).isNotEqualTo(initialTrustHash);
            assertThat(updated.trustStore().sizeBytes()).isEqualTo(initialTrustSize + 4);
        });
    }
}
