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

import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Channel with a fixed number of available permits. This can be used to enforce a per-route request limit.
 */
final class FixedLimitedChannel implements CompositeLimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(FixedLimitedChannel.class);

    private final CompositeLimitedChannel delegate;
    private final AtomicInteger usedPermits = new AtomicInteger(0);
    private final Meter limitedMeter;
    private final int totalPermits;
    private final Runnable returnPermit;

    FixedLimitedChannel(CompositeLimitedChannel delegate, int totalPermits, DialogueClientMetrics metrics) {
        this.delegate = delegate;
        this.totalPermits = totalPermits;
        this.limitedMeter = metrics.limited(getClass().getSimpleName());
        // Doesn't check for integer overflow, we don't have enough threads for that to occur.
        Preconditions.checkArgument(totalPermits <= 1_000_000, "total permits must not exceed one million");
        this.returnPermit = usedPermits::decrementAndGet;
    }

    @Override
    public LimitedResponse maybeExecute(Endpoint endpoint, Request request) {
        boolean failedToOptimisticallyAcquirePermit = usedPermits.incrementAndGet() > totalPermits;
        if (failedToOptimisticallyAcquirePermit) {
            returnPermit.run();
            limitedMeter.mark();
            logExhaustion(endpoint);
            return LimitedResponses.limited();
        }
        boolean resetOptimisticallyConsumedPermit = true;
        try {
            LimitedResponse response = delegate.maybeExecute(endpoint, request);
            Optional<ListenableFuture<Response>> result = LimitedResponses.getResponse(response);
            if (result.isPresent()) {
                result.get().addListener(returnPermit, MoreExecutors.directExecutor());
                resetOptimisticallyConsumedPermit = false;
            }
            return response;
        } finally {
            if (resetOptimisticallyConsumedPermit) {
                returnPermit.run();
            }
        }
    }

    private void logExhaustion(Endpoint endpoint) {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Permits have been exhausted",
                    SafeArg.of("service", endpoint.serviceName()),
                    SafeArg.of("endpoint", endpoint.endpointName()),
                    SafeArg.of("totalPermits", totalPermits));
        }
    }
}
