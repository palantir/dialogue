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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.api.config.service.PartialServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.TestConfigurations;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.refreshable.Disposable;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class DialogueDnsResolutionWorkerTest {
    private static final class StaticDnsResolver implements DialogueDnsResolver {

        private final ImmutableSetMultimap<String, InetAddress> resolvedHosts;

        StaticDnsResolver(ImmutableSetMultimap<String, InetAddress> resolvedHosts) {
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

    private static final class RotatingStaticDnsResolver implements DialogueDnsResolver {
        private final Map<String, Deque<InetAddress>> resolvedHosts = new HashMap<>();
        private long lastResolveTime = System.currentTimeMillis();

        RotatingStaticDnsResolver(Map<String, List<InetAddress>> staticMapping) {
            staticMapping.forEach((hostname, addresses) -> addresses.forEach(address -> {
                if (!resolvedHosts.containsKey(hostname)) {
                    Deque<InetAddress> resolvedAddresses = new ArrayDeque<>();
                    resolvedAddresses.add(address);
                    resolvedHosts.put(hostname, resolvedAddresses);
                } else {
                    Deque<InetAddress> resolvedAddresses = resolvedHosts.get(hostname);
                    resolvedAddresses.add(address);
                }
            }));
        }

        private InetAddress getNextAddress(String hostname) {
            Deque<InetAddress> addresses = resolvedHosts.get(hostname);
            if (addresses == null) {
                return null;
            }

            long elapsedMillis = System.currentTimeMillis() - lastResolveTime;
            // rotate resolved addresses every 5 seconds;
            long nRotations = elapsedMillis / 5000;
            if (nRotations > 100) {
                throw new RuntimeException("too many rotations");
            }

            while (nRotations > 0) {
                addresses.add(addresses.pop());
                --nRotations;
            }

            return addresses.getFirst();
        }

        @Override
        public ImmutableSet<InetAddress> resolve(String hostname) {
            InetAddress next = getNextAddress(hostname);
            lastResolveTime = System.currentTimeMillis();
            if (next == null) {
                return ImmutableSet.of();
            }
            return ImmutableSet.of(next);
        }

        @Override
        public ImmutableSetMultimap<String, InetAddress> resolve(Iterable<String> hostNames) {
            ImmutableSetMultimap.Builder<String, InetAddress> builder = ImmutableSetMultimap.builder();
            hostNames.forEach(hostname -> {
                ImmutableSet<InetAddress> addresses = resolve(hostname);
                builder.putAll(hostname, addresses);
            });
            return builder.build();
        }
    }

    @Test
    public void testResolvedAddressesChangesAfterStartup() throws Exception {
        InetAddress address1 = InetAddress.getByName("1.2.3.4");
        InetAddress address2 = InetAddress.getByName("5.6.7.8");

        DialogueDnsResolver resolver =
                new RotatingStaticDnsResolver(ImmutableMap.of("foo.com", ImmutableList.of(address1, address2)));

        String fooUri = "https://foo.com:12345/foo";
        ServicesConfigBlock initialState = ServicesConfigBlock.builder()
                .defaultSecurity(TestConfigurations.SSL_CONFIG)
                .putServices(
                        "foo",
                        PartialServiceConfiguration.builder().addUris(fooUri).build())
                .build();
        SettableRefreshable<ServicesConfigBlock> inputRefreshable = Refreshable.create(initialState);
        SettableRefreshable<ServicesConfigBlockWithResolvedHosts> receiverRefreshable = Refreshable.create(null);
        DialogueDnsResolutionWorker worker = new DialogueDnsResolutionWorker(resolver, receiverRefreshable::update);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            executorService.execute(worker);
            Disposable disposable = inputRefreshable.subscribe(worker::update);

            Awaitility.waitAtMost(Duration.ofSeconds(2)).untilAsserted(() -> {
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

            // resolved address should rotate after ~5 seconds
            Awaitility.waitAtMost(Duration.ofSeconds(7)).untilAsserted(() -> {
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
        DialogueDnsResolver resolver = new StaticDnsResolver(ImmutableSetMultimap.<String, InetAddress>builder()
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
        DialogueDnsResolutionWorker worker = new DialogueDnsResolutionWorker(resolver, receiverRefreshable::update);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            executorService.execute(worker);

            assertThat(receiverRefreshable.get()).isNull();

            Disposable disposable = inputRefreshable.subscribe(worker::update);

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
            disposable.dispose();
        } finally {
            worker.shutdown();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, 5, TimeUnit.MINUTES))
                    .isTrue();
        }
    }
}
