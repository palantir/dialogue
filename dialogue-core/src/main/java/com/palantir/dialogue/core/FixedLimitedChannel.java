/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.dialogue.core;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Channel with a fixed number of available permits. This can be used to enforce a per-route request limit.
 */
final class FixedLimitedChannel implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(FixedLimitedChannel.class);

    private final LimitedChannel delegate;
    private final AtomicInteger availablePermits;

    FixedLimitedChannel(LimitedChannel delegate, int permits) {
        this.delegate = delegate;
        this.availablePermits = new AtomicInteger(permits);
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        // Doesn't check for integer overflow, we don't have enough threads for that to occur.
        if (availablePermits.decrementAndGet() < 0) {
            availablePermits.incrementAndGet();
            if (log.isDebugEnabled()) {
                log.debug(
                        "Permits have been exhausted",
                        SafeArg.of("service", endpoint.serviceName()),
                        SafeArg.of("endpoint", endpoint.endpointName()));
            }
            return Optional.empty();
        }
        boolean resetPermit = true;
        try {
            Optional<ListenableFuture<Response>> result = delegate.maybeExecute(endpoint, request);
            if (result.isPresent()) {
                result.get().addListener(availablePermits::incrementAndGet, MoreExecutors.directExecutor());
                resetPermit = false;
            }
            return result;
        } finally {
            if (resetPermit) {
                availablePermits.incrementAndGet();
            }
        }
    }
}
