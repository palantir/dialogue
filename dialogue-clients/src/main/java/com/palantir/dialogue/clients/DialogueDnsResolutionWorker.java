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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.SettableRefreshable;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import javax.annotation.Nullable;

final class DialogueDnsResolutionWorker implements Runnable {
    private static final SafeLogger log = SafeLoggerFactory.get(DialogueDnsResolutionWorker.class);

    @Nullable
    @GuardedBy("this")
    private ServicesConfigBlock inputState;

    private volatile boolean shutdownRequested;
    private final DialogueDnsResolver resolver;
    private final Duration dnsRefreshInterval;
    private final WeakReference<SettableRefreshable<ServicesConfigBlockWithResolvedHosts>> receiver;

    DialogueDnsResolutionWorker(
            DialogueDnsResolver resolver,
            Duration dnsRefreshInterval,
            SettableRefreshable<ServicesConfigBlockWithResolvedHosts> receiver) {
        this.resolver = resolver;
        this.dnsRefreshInterval = dnsRefreshInterval;
        this.receiver = new WeakReference<>(receiver);
        this.shutdownRequested = false;
    }

    void update(ServicesConfigBlock scb) {
        // blocks the calling thread
        doUpdate(scb);
    }

    void shutdown() {
        shutdownRequested = true;
    }

    @Override
    public void run() {
        // TODO(dns): We can handle this with a scheduled executor instead, allowing a single scheduler to be reused
        // for multiple factories.
        while (!shutdownRequested) {
            try {
                // check for updates to scb state first
                Thread.sleep(dnsRefreshInterval.toMillis());
                try {
                    if (receiver.get() == null) {
                        shutdownRequested = true;
                        log.info("Output refreshable has been garbage collected, no need to continue polling");
                    } else {
                        doUpdate(null);
                    }
                } catch (Throwable t) {
                    log.error("Scheduled DNS update failed", t);
                }
            } catch (InterruptedException e) {
                if (shutdownRequested) {
                    log.debug("interrupted checking for updates after shutdown", e);
                } else {
                    log.warn("interrupted checking for updates", e);
                    shutdownRequested = true;
                }
            }
        }
    }

    private synchronized void doUpdate(@Nullable ServicesConfigBlock updatedInputState) {
        if (updatedInputState != null && !updatedInputState.equals(inputState)) {
            inputState = updatedInputState;
        }

        // resolve all names in the current state, if there is one
        if (inputState != null) {
            ImmutableSet<String> allHosts = inputState.services().values().stream()
                    .flatMap(psc -> psc.uris().stream()
                            .map(uriString -> {
                                try {
                                    URI uri = new URI(uriString);
                                    return uri.getHost();
                                } catch (URISyntaxException | RuntimeException e) {
                                    log.debug("Failed to parse URI", e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull))
                    .collect(ImmutableSet.toImmutableSet());
            ImmutableSetMultimap<String, InetAddress> resolvedHosts = resolver.resolve(allHosts);
            ServicesConfigBlockWithResolvedHosts newResolvedState =
                    ImmutableServicesConfigBlockWithResolvedHosts.of(inputState, resolvedHosts);
            SettableRefreshable<ServicesConfigBlockWithResolvedHosts> refreshable = receiver.get();
            if (refreshable != null) {
                refreshable.update(newResolvedState);
            } else {
                log.info("Attempted to update ServicesConfigBlockWithResolvedHosts refreshable "
                        + "which has already been garbage collected");
            }
        }
    }
}
