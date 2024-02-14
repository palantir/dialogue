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
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.clients.DialogueDnsDiscoveryMetrics.Resolution_Result;
import com.palantir.dialogue.core.TargetUri;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.Refreshable;
import com.palantir.refreshable.SettableRefreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Internal utility functionality for handling dialogue DNS resolution. */
final class DialogueDnsResolution {
    private static final SafeLogger log = SafeLoggerFactory.get(DialogueDnsResolution.class);

    // This shouldn't be a static singleton if we can avoid it.
    private static final ScheduledExecutorService SINGLETON_SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    static RefreshableUris refreshableUris(String serviceName, ClientConfiguration configuration) {
        return refreshableUris(
                serviceName, configuration.taggedMetricRegistry(), configuration.uris(), configuration.proxy());
    }

    static RefreshableUris refreshableUris(
            String channelName, TaggedMetricRegistry registry, List<String> uris, ProxySelector selector) {
        // Metrics for total number of dns refresh tasks are running per channel. This way
        // we can detect if one is removed unexpectedly.
        DialogueDnsDiscoveryMetrics metrics = DialogueDnsDiscoveryMetrics.of(registry);
        Timer successTimer = metrics.resolution()
                .channelName(channelName)
                .result(Resolution_Result.SUCCESS)
                .build();
        Timer failureTimer = metrics.resolution()
                .channelName(channelName)
                .result(Resolution_Result.FAILURE)
                .build();
        ImmutableList<TargetUri> initial = resolveUris(channelName, uris, selector, successTimer, failureTimer);
        SettableRefreshable<ImmutableList<TargetUri>> refreshable = Refreshable.create(initial);
        Counter scheduledTasks = metrics.scheduled(channelName);
        ScheduledFuture<?> dnsRefreshFuture = SINGLETON_SCHEDULER.scheduleWithFixedDelay(
                () -> {
                    try {
                        refreshable.update(resolveUris(channelName, uris, selector, successTimer, failureTimer));
                    } catch (Throwable t) {
                        log.warn("Failed to refresh URIs for channel {}", SafeArg.of("channel", channelName), t);
                    }
                },
                5,
                5,
                TimeUnit.SECONDS);
        // Increment after scheduling to ensure the task was scheduled as expected
        scheduledTasks.inc();
        return new RefreshableUris(channelName, refreshable, dnsRefreshFuture, scheduledTasks);
    }

    static final class RefreshableUris {

        @Safe
        private final String channelName;

        private final Refreshable<ImmutableList<TargetUri>> uris;
        private final ScheduledFuture<?> dnsRefreshFuture;
        private final Counter scheduledTasks;

        private RefreshableUris(
                @Safe String channelName,
                Refreshable<ImmutableList<TargetUri>> uris,
                ScheduledFuture<?> dnsRefreshFuture,
                Counter scheduledTasks) {
            this.channelName = channelName;
            this.uris = uris;
            this.dnsRefreshFuture = dnsRefreshFuture;
            this.scheduledTasks = scheduledTasks;
        }

        Refreshable<ImmutableList<TargetUri>> uris() {
            return uris;
        }

        /** {@link #stop()} may be called to avoid polling DNS for updates to the returned refreshable. */
        void stop() {
            if (dnsRefreshFuture.cancel(false)) {
                scheduledTasks.dec();
                log.info("Unregistered scheduled DNS refresh task", SafeArg.of("channel", channelName));
            }
        }
    }

    static ImmutableList<TargetUri> resolveUris(
            @Safe String channelName,
            List<String> uris,
            ProxySelector selector,
            Timer successTimer,
            Timer failureTimer) {
        // TODO(ckozak): cache dns lookups? JVM already does this, so it may not be terribly helpful.
        List<TargetUri> targets = new ArrayList<>();
        for (String uri : uris) {
            URI parsed = URI.create(uri);
            String host = parsed.getHost();
            boolean isInetAddress = InetAddresses.isInetAddress(host);
            boolean usesProxy = usesProxy(selector, parsed);
            // If the input is already an IP address, or uses a proxy,
            // we do not attempt DNS resolution.
            if (isInetAddress || usesProxy) {
                targets.add(TargetUri.builder().uri(uri).build());
            } else {
                boolean success = false;
                long startTime = System.nanoTime();
                try {
                    InetAddress[] addresses = InetAddress.getAllByName(host);
                    success = true;
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Resolved {} addresses ({}) for channel {}",
                                SafeArg.of("numAddresses", addresses.length),
                                UnsafeArg.of("addresses", addresses),
                                SafeArg.of("channel", channelName));
                    }
                    // Avoid forcing a refresh when DNS ordering changes.
                    // Note that some environments provide priority-ordered DNS results where the first
                    // result is expected to be the most efficient. In such environments, it's unclear
                    // that we should replace the preferred order.
                    // In a follow-up change, we may want to handle this in a way that preserves the
                    // original order without refreshing when the same entries change position in subsequent polls.
                    Arrays.sort(addresses, Comparator.comparing(InetAddress::getAddress, Arrays::compare));
                    for (InetAddress address : addresses) {
                        targets.add(TargetUri.builder()
                                .uri(uri)
                                .resolvedAddress(address)
                                .build());
                    }
                    // TODO(ckozak): Add metrics describing number of resolved addresses per-channel
                } catch (UnknownHostException e) {
                    // We could provide the unresolved hostname, however we're probably better off
                    // if it's entirely absent from the list, so other nodes may take priority.
                    log.warn("DNS Resolution failed for channel {}", SafeArg.of("channel", channelName), e);
                } finally {
                    Timer timer = success ? successTimer : failureTimer;
                    timer.update(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
                }
            }
        }
        return ImmutableList.copyOf(targets);
    }

    private static boolean usesProxy(ProxySelector proxySelector, URI uri) {
        try {
            List<Proxy> proxies = proxySelector.select(uri);
            return proxies.stream().noneMatch(proxy -> Proxy.Type.DIRECT.equals(proxy.type()));
        } catch (RuntimeException e) {
            // Fall back to the simple path without scheduling recurring DNS resolution.
            return true;
        }
    }

    private DialogueDnsResolution() {}
}
