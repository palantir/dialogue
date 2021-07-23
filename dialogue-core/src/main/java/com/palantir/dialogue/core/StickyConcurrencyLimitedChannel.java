/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Behavior;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.util.Optional;

final class StickyConcurrencyLimitedChannel implements LimitedChannel {

    private static final SafeLogger log = SafeLoggerFactory.get(StickyConcurrencyLimitedChannel.class);

    private final NeverThrowLimitedChannel delegate;
    private final CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter;
    private final String channelNameForLogging;

    @VisibleForTesting
    StickyConcurrencyLimitedChannel(
            LimitedChannel delegate,
            CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter,
            String channelNameForLogging) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.limiter = limiter;
        this.channelNameForLogging = channelNameForLogging;
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> maybePermit =
                limiter.acquire(limitEnforcement);
        if (maybePermit.isPresent()) {
            CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit permit = maybePermit.get();
            logPermitAcquired();

            // This is a trade-off to solve an edge case where the first request on this channel could get
            // concurrency limited: we would then have to rely on another request coming in to try scheduling the
            // first request again. If that request never came, we would lock up.
            // To combat that we have a way to instruct downstream channel to ignore its capacity limits,
            // and let the request through.
            Optional<ListenableFuture<Response>> result = delegate.maybeExecute(
                    endpoint,
                    request,
                    permit.isOnlyInFlight() ? LimitEnforcement.DANGEROUS_BYPASS_LIMITS : limitEnforcement);
            if (result.isPresent()) {
                DialogueFutures.addDirectCallback(result.get(), permit);
                return result;
            } else {
                maybePermit.get().dropped();
                return Optional.empty();
            }
        } else {
            logPermitRefused();
            return Optional.empty();
        }
    }

    static LimitedChannel create(LimitedChannel channel, String channelName) {
        return new StickyConcurrencyLimitedChannel(
                channel, new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(Behavior.STICKY), channelName);
    }

    private void logPermitAcquired() {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Sending {}/{} on {}",
                    SafeArg.of("inflight", limiter.getInflight()),
                    SafeArg.of("max", limiter.getLimit()),
                    SafeArg.of("channel", channelNameForLogging));
        }
    }

    private void logPermitRefused() {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Limited {} on {}",
                    SafeArg.of("max", limiter.getLimit()),
                    SafeArg.of("channel", channelNameForLogging));
        }
    }

    @Override
    public String toString() {
        return "StickyConcurrencyLimitedChannel{delegate=" + delegate + ", name=" + channelNameForLogging + '}';
    }
}
