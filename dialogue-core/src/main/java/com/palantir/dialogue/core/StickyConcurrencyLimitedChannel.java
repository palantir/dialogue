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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Behavior;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StickyConcurrencyLimitedChannel implements LimitedChannel {

    private static final Logger log = LoggerFactory.getLogger(StickyConcurrencyLimitedChannel.class);

    private final LimitedChannel delegate;
    private final CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter;
    private final String channelNameForLogging;

    StickyConcurrencyLimitedChannel(LimitedChannel delegate, String channelNameForLogging) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.limiter = new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(Behavior.STICKY);
        this.channelNameForLogging = channelNameForLogging;
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> maybePermit = limiter.acquire();
        if (maybePermit.isPresent()) {
            CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit permit = maybePermit.get();
            logPermitAcquired();
            Optional<ListenableFuture<Response>> result = delegate.maybeExecute(endpoint, request);
            if (result.isPresent()) {
                DialogueFutures.addDirectCallback(result.get(), permit);
                return result;
            } else {
                maybePermit.get().onFailure(StickyChannelRejectedException.INSTANCE.exception());
                return Optional.empty();
            }
        } else {
            logPermitRefused();
            return Optional.empty();
        }
    }

    static LimitedChannel createForQueueKey(
            LimitedChannel channel, String channelName, StickyRoutingChannel.QueueKey queueKey) {
        return new StickyConcurrencyLimitedChannel(channel, channelName + "{queueKey=" + queueKey.toString() + "}");
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
