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

import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder.SetMultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.SettableRefreshable;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

final class DialogueDnsResolutionWorker<INPUT> implements Runnable {
    private static final SafeLogger log = SafeLoggerFactory.get(DialogueDnsResolutionWorker.class);

    @Nullable
    @GuardedBy("this")
    private INPUT inputState;

    @GuardedBy("this")
    private ImmutableMap<@Safe String, @Safe Integer> previousUnresolvedByChannel = ImmutableMap.of();

    private final DnsPollingSpec<INPUT> spec;
    private final DialogueDnsResolver resolver;
    private final WeakReference<SettableRefreshable<DnsResolutionResults<INPUT>>> receiver;
    private final Timer updateTimer;

    DialogueDnsResolutionWorker(
            DnsPollingSpec<INPUT> spec,
            DialogueDnsResolver resolver,
            SettableRefreshable<DnsResolutionResults<INPUT>> receiver,
            Timer updateTimer) {
        this.spec = spec;
        this.resolver = resolver;
        this.receiver = new WeakReference<>(receiver);
        this.updateTimer = updateTimer;
    }

    void update(INPUT input) {
        // blocks the calling thread
        doUpdate(input);
    }

    @Override
    public void run() {
        try {
            if (receiver.get() == null) {
                // n.b. We could throw an exception here to specifically cause the executor to deschedule, however
                // this logging may be helpful in informing us of problems in the system.
                log.info(
                        "Output refreshable has been garbage collected, no need to continue polling",
                        SafeArg.of("kind", spec.kind()));
            } else {
                doUpdate(null);
            }
        } catch (Throwable t) {
            log.error("Scheduled DNS update failed", SafeArg.of("kind", spec.kind()), t);
        }
    }

    private synchronized void doUpdate(@Nullable INPUT updatedInputState) {
        if (updatedInputState != null && !updatedInputState.equals(inputState)) {
            inputState = updatedInputState;
        }

        // resolve all names in the current state, if there is one
        if (inputState != null) {
            long start = System.nanoTime();
            ImmutableSet<String> allHosts = spec.extractUris(inputState)
                    .filter(Objects::nonNull)
                    // n.b. we could filter out hosts with specify a proxy and mesh-mode
                    // uris here, however it's simpler to resolve everything, and use what
                    // we need when TargetUri instances are constructed.
                    .map(DnsSupport::tryGetHost)
                    .filter(Objects::nonNull)
                    .collect(ImmutableSet.toImmutableSet());
            ImmutableSetMultimap<String, InetAddress> resolvedHosts = resolver.resolve(allHosts);
            ImmutableSet<@Unsafe String> unresolvedHosts = allHosts.stream()
                    .filter(host -> !resolvedHosts.containsKey(host))
                    .collect(ImmutableSet.toImmutableSet());
            ImmutableMap<@Safe String, @Safe Integer> unresolvedByChannel =
                    countHostsByChannelName(inputState, unresolvedHosts);
            // Only emit logging upon a state change
            if (!Objects.equals(unresolvedByChannel, previousUnresolvedByChannel)) {
                previousUnresolvedByChannel = unresolvedByChannel;
                if (!unresolvedByChannel.isEmpty()) {
                    Map<@Safe String, @Safe Integer> resolvedByChannel =
                            countHostsByChannelName(inputState, resolvedHosts.keySet());
                    log.info(
                            "Failed to resolve all hostnames",
                            SafeArg.of("kind", spec.kind()),
                            SafeArg.of("unresolvedByChannel", unresolvedByChannel),
                            SafeArg.of("resolvedByChannel", resolvedByChannel));
                } else {
                    // This will be logged once after a dns failure is resolved
                    log.info("Successfully resolved all hostnames", SafeArg.of("kind", spec.kind()));
                }
            }
            DnsResolutionResults<INPUT> newResolvedState =
                    ImmutableDnsResolutionResults.of(inputState, Optional.of(resolvedHosts));
            SettableRefreshable<DnsResolutionResults<INPUT>> refreshable = receiver.get();
            if (refreshable != null) {
                logResolvedStateChange(refreshable.get(), newResolvedState);
                refreshable.update(newResolvedState);
            } else {
                log.info(
                        "Attempted to update DNS output refreshable which has already been garbage collected",
                        SafeArg.of("kind", spec.kind()));
            }
            long end = System.nanoTime();
            updateTimer.update(end - start, TimeUnit.NANOSECONDS);
        }
    }

    private void logResolvedStateChange(
            DnsResolutionResults<INPUT> currentResolvedState, DnsResolutionResults<INPUT> newResolvedState) {
        if (currentResolvedState == null && newResolvedState == null) {
            return;
        }

        if (currentResolvedState == null || newResolvedState == null) {
            log.info("One or more resolved host addresses have changed");
            return;
        }

        if (currentResolvedState.resolvedHosts().isEmpty()
                && newResolvedState.resolvedHosts().isEmpty()) {
            return;
        }

        if (currentResolvedState.resolvedHosts().isEmpty()
                || newResolvedState.resolvedHosts().isEmpty()) {
            log.info("One or more resolved host addresses have changed");
            return;
        }

        if (!currentResolvedState
                .resolvedHosts()
                .get()
                .equals(newResolvedState.resolvedHosts().get())) {
            log.info("One or more resolved host addresses have changed");
        }
    }

    @GuardedBy("this")
    private ImmutableMap<@Safe String, @Safe Integer> countHostsByChannelName(INPUT input, ImmutableSet<String> hosts) {
        if (hosts.isEmpty()) {
            // Short circuit the trivial case
            return ImmutableMap.of();
        }
        SetMultimap<@Safe String, @Unsafe String> channelNameToHostnames =
                SetMultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
        for (@Unsafe String host : hosts) {
            for (String channelName : spec.describeHostname(input, host)) {
                channelNameToHostnames.put(channelName, host);
            }
        }
        return ImmutableMap.copyOf(Maps.transformValues(channelNameToHostnames.asMap(), Collection::size));
    }
}
