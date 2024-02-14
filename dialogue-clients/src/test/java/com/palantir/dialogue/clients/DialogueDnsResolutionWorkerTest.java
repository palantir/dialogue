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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class DialogueDnsResolutionWorkerTest {
    @Test
    public void testFoo() {
        String fooUri = "https://localhost:12345/foo";
        ServicesConfigBlock initialState = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        "foo",
                        PartialServiceConfiguration.builder().addUris(fooUri).build())
                .build();
        SettableRefreshable<ServicesConfigBlock> inputRefreshable = Refreshable.create(initialState);
        SettableRefreshable<ServicesConfigBlockWithResolvedHosts> receiverRefreshable = Refreshable.create(null);
        DialogueDnsResolutionWorker worker = new DialogueDnsResolutionWorker(receiverRefreshable);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            executorService.execute(worker);

            assertThat(receiverRefreshable.get()).isNull();

            inputRefreshable.map(worker::update);

            Awaitility.waitAtMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(receiverRefreshable.get()).isNotNull());

            System.out.println(receiverRefreshable.get().targets());

            assertThat(receiverRefreshable.get().scb()).isEqualTo(initialState);
            assertThat(receiverRefreshable.get().targets().keySet().size()).isEqualTo(1);
            assertThat(receiverRefreshable.get().targets().containsKey(fooUri)).isTrue();
            assertThat(receiverRefreshable.get().targets().get(fooUri))
                    .anyMatch(t -> t.uri().equals(fooUri)
                            && t.resolvedAddress().isPresent()
                            && t.resolvedAddress().get().isLoopbackAddress());

            String barUri = "https://localhost:12345/bar";
            ServicesConfigBlock newState = ServicesConfigBlock.builder()
                    .from(initialState)
                    .putServices(
                            "bar",
                            PartialServiceConfiguration.builder()
                                    .addUris(barUri)
                                    .build())
                    .build();

            inputRefreshable.update(newState);

            Awaitility.waitAtMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () -> assertThat(receiverRefreshable.get().scb()).isEqualTo(newState));

            System.out.println(receiverRefreshable.get().targets());

        } finally {
            worker.shutdown();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, 5, TimeUnit.MINUTES))
                    .isTrue();
        }
    }
}
