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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.TestConfigurations;
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
    private static final class TestDnsResolver implements DialogueDnsResolver {

        private final ImmutableSetMultimap<String, InetAddress> resolvedHosts;

        TestDnsResolver(ImmutableSetMultimap<String, InetAddress> resolvedHosts) {
            this.resolvedHosts = resolvedHosts;
        }

        @Override
        public ImmutableSet<InetAddress> resolve(String hostname) {
            return resolvedHosts.get(hostname);
        }

        @Override
        public ImmutableSetMultimap<String, InetAddress> resolve(Iterable<String> hostNames) {
            ImmutableSetMultimap.Builder<String, InetAddress> builder = ImmutableSetMultimap.builder();
            hostNames.forEach(hostname -> {
                ImmutableSet<InetAddress> addresses = resolvedHosts.get(hostname);
                builder.putAll(hostname, addresses);
            });
            return builder.build();
        }
    }

    @Test
    public void testInputStateChangeAddsAdditionalResolvedHost() {
        DialogueDnsResolver resolver = new TestDnsResolver(ImmutableSetMultimap.<String, InetAddress>builder()
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
        DialogueDnsResolutionWorker worker = new DialogueDnsResolutionWorker(resolver, receiverRefreshable);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            executorService.execute(worker);

            assertThat(receiverRefreshable.get()).isNull();

            inputRefreshable.map(worker::update);

            Awaitility.waitAtMost(Duration.ofSeconds(10))
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

            Awaitility.waitAtMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () -> assertThat(receiverRefreshable.get().scb()).isEqualTo(newState));

            assertThat(receiverRefreshable.get().resolvedHosts().keySet().size())
                    .isEqualTo(2);
            assertThat(receiverRefreshable.get().resolvedHosts().containsKey("bar.com"))
                    .isTrue();
            assertThat(receiverRefreshable.get().resolvedHosts().get("bar.com"))
                    .anyMatch(InetAddress::isLoopbackAddress);
        } finally {
            worker.shutdown();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, 5, TimeUnit.MINUTES))
                    .isTrue();
        }
    }
}