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

import com.google.common.collect.ImmutableSetMultimap;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.dialogue.core.DialogueDnsResolver;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.refreshable.SettableRefreshable;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

final class DialogueDnsResolutionWorker implements Runnable {
    private static final SafeLogger log = SafeLoggerFactory.get(DialogueDnsResolutionWorker.class);
    private static final BlockingQueue<ServicesConfigBlock> updatesQueue = new LinkedBlockingQueue<>();

    @Nullable
    private ServicesConfigBlock inputState;

    @Nullable
    private ServicesConfigBlockWithResolvedHosts resolvedState;

    private volatile boolean shutdownRequested;
    private final DialogueDnsResolver resolver;
    private final SettableRefreshable<ServicesConfigBlockWithResolvedHosts> receiver;

    DialogueDnsResolutionWorker(
            DialogueDnsResolver resolver, SettableRefreshable<ServicesConfigBlockWithResolvedHosts> receiver) {
        this.resolver = resolver;
        this.receiver = receiver;
        this.shutdownRequested = false;
    }

    boolean update(ServicesConfigBlock scb) {
        if (inputState == null) {
            // blocks the calling thread for the first update
            doUpdate(scb);
            return true;
        }
        return updatesQueue.offer(scb);
    }

    void shutdown() {
        shutdownRequested = true;
    }

    @Override
    public void run() {
        while (!shutdownRequested) {
            try {
                // check for updates to scb state first
                ServicesConfigBlock updatedInputState = updatesQueue.poll(5, TimeUnit.SECONDS);
                doUpdate(updatedInputState);
            } catch (InterruptedException e) {
                log.warn("interrupted checking for updates", e);
                shutdownRequested = true;
            }
        }
    }

    private void doUpdate(ServicesConfigBlock updatedInputState) {
        if (updatedInputState != null && !updatedInputState.equals(inputState)) {
            inputState = updatedInputState;
        }

        // resolve all names in the current state, if there is one
        if (inputState != null) {
            List<String> allHosts = inputState.services().values().stream()
                    .flatMap(psc -> psc.uris().stream().map(URI::create).map(URI::getHost))
                    .collect(Collectors.toList());
            ImmutableSetMultimap<String, InetAddress> resolvedHosts = resolver.resolve(allHosts);
            ServicesConfigBlockWithResolvedHosts newResolvedState =
                    ImmutableServicesConfigBlockWithResolvedHosts.of(inputState, resolvedHosts);
            if (resolvedState == null || !newResolvedState.equals(resolvedState)) {
                resolvedState = newResolvedState;
                receiver.update(resolvedState);
            }
        }
    }
}
