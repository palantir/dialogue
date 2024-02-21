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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.dialogue.util.MapBasedDnsResolver;
import com.palantir.refreshable.Disposable;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class DialogueDnsResolutionWorkerTest {

    @Test
    public void testResolvedAddressesChangesAfterStartup() throws Exception {
        InetAddress address1 = InetAddress.getByName("1.2.3.4");
        InetAddress address2 = InetAddress.getByName("5.6.7.8");

        SetMultimap<String, InetAddress> resolvedAddresses = LinkedHashMultimap.create();
        resolvedAddresses.put("foo.com", address1);
        DialogueDnsResolver resolver = new MapBasedDnsResolver(resolvedAddresses);

        String fooUri = "https://foo.com:12345/foo";
        ServicesConfigBlock initialState = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        "foo",
                        PartialServiceConfiguration.builder().addUris(fooUri).build())
                .build();
        SettableRefreshable<ServicesConfigBlock> inputRefreshable = Refreshable.create(initialState);
        SettableRefreshable<ServicesConfigBlockWithResolvedHosts> receiverRefreshable = Refreshable.create(null);
        DialogueDnsResolutionWorker worker =
                new DialogueDnsResolutionWorker(resolver, Duration.ofMillis(500), receiverRefreshable);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            executorService.execute(worker);
            Disposable disposable = inputRefreshable.subscribe(worker::update);

            Awaitility.waitAtMost(Duration.ofSeconds(1)).untilAsserted(() -> {
                assertThat(receiverRefreshable.get()).isNotNull();
                assertThat(receiverRefreshable.get().resolvedHosts().containsKey("foo.com"))
                        .isTrue();
                assertThat(receiverRefreshable
                                .get()
                                .resolvedHosts()
                                .get("foo.com")
                                .size())
                        .isEqualTo(1);
                assertThat(receiverRefreshable.get().resolvedHosts().get("foo.com"))
                        .allMatch(address1::equals);
                assertThat(receiverRefreshable.get().resolvedHosts().get("foo.com"))
                        .noneMatch(address2::equals);
            });

            // simulate a change for the resolved addresses after the worker thread has started
            resolvedAddresses.replaceValues("foo.com", ImmutableList.of(address2));

            Awaitility.waitAtMost(Duration.ofSeconds(1)).untilAsserted(() -> {
                assertThat(receiverRefreshable.get()).isNotNull();
                assertThat(receiverRefreshable.get().resolvedHosts().containsKey("foo.com"))
                        .isTrue();
                assertThat(receiverRefreshable
                                .get()
                                .resolvedHosts()
                                .get("foo.com")
                                .size())
                        .isEqualTo(1);
                assertThat(receiverRefreshable.get().resolvedHosts().get("foo.com"))
                        .allMatch(address2::equals);
                assertThat(receiverRefreshable.get().resolvedHosts().get("foo.com"))
                        .noneMatch(address1::equals);
            });
            disposable.dispose();
        } finally {
            worker.shutdown();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, 5, TimeUnit.MINUTES))
                    .isTrue();
        }
    }

    @Test
    public void testInputStateChangeAddsAdditionalResolvedHost() {
        DialogueDnsResolver resolver = new MapBasedDnsResolver(ImmutableSetMultimap.<String, InetAddress>builder()
                .put("foo.com", InetAddress.getLoopbackAddress())
                .put("bar.com", InetAddress.getLoopbackAddress())
                .build());

        String fooUri = "https://foo.com:12345/foo";
        ServicesConfigBlock initialState = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        "foo",
                        PartialServiceConfiguration.builder().addUris(fooUri).build())
                .build();
        SettableRefreshable<ServicesConfigBlock> inputRefreshable = Refreshable.create(initialState);
        SettableRefreshable<ServicesConfigBlockWithResolvedHosts> receiverRefreshable = Refreshable.create(null);
        DialogueDnsResolutionWorker worker =
                new DialogueDnsResolutionWorker(resolver, Duration.ofMillis(500), receiverRefreshable);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            executorService.execute(worker);

            assertThat(receiverRefreshable.get()).isNull();

            Disposable disposable = inputRefreshable.subscribe(worker::update);

            Awaitility.waitAtMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(receiverRefreshable.get()).isNotNull());

            assertThat(receiverRefreshable.get().scb()).isEqualTo(initialState);
            assertThat(receiverRefreshable.get().resolvedHosts().keySet().size())
                    .isEqualTo(1);
            assertThat(receiverRefreshable.get().resolvedHosts().containsKey("foo.com"))
                    .isTrue();
            assertThat(receiverRefreshable.get().resolvedHosts().get("foo.com"))
                    .anyMatch(InetAddress::isLoopbackAddress);

            String barUri = "https://bar.com:12345/bar";
            ServicesConfigBlock newState = ServicesConfigBlock.builder()
                    .from(initialState)
                    .putServices(
                            "bar",
                            PartialServiceConfiguration.builder()
                                    .addUris(barUri)
                                    .build())
                    .build();

            inputRefreshable.update(newState);

            Awaitility.waitAtMost(Duration.ofSeconds(1))
                    .untilAsserted(
                            () -> assertThat(receiverRefreshable.get().scb()).isEqualTo(newState));

            assertThat(receiverRefreshable.get().resolvedHosts().keySet().size())
                    .isEqualTo(2);
            assertThat(receiverRefreshable.get().resolvedHosts().containsKey("bar.com"))
                    .isTrue();
            assertThat(receiverRefreshable.get().resolvedHosts().get("bar.com"))
                    .anyMatch(InetAddress::isLoopbackAddress);
            disposable.dispose();
        } finally {
            worker.shutdown();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, 5, TimeUnit.MINUTES))
                    .isTrue();
        }
    }
}
