/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Behavior;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.stream.LongStream;

/**
 * A channel that monitors the successes and failures of requests in order to determine the number of concurrent
 * requests allowed to a particular channel. If the channel's concurrency limit has been reached, the
 * {@link LimitedChannel#maybeExecute} method returns empty.
 */
final class ConcurrencyLimitedChannel implements LimitedChannel {
    private static final SafeLogger log = SafeLoggerFactory.get(ConcurrencyLimitedChannel.class);

    private final NeverThrowChannel delegate;
    private final CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter;
    private final String channelNameForLogging;

    static LimitedChannel createForHost(Config cf, Channel channel, int uriIndex) {
        TaggedMetricRegistry metrics = cf.clientConf().taggedMetricRegistry();
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = createLimiter(Behavior.HOST_LEVEL);
        ConcurrencyLimitedChannelInstrumentation instrumentation =
                new HostConcurrencyLimitedChannelInstrumentation(cf.channelName(), uriIndex, limiter, metrics);
        return new ConcurrencyLimitedChannel(channel, limiter, instrumentation);
    }

    /**
     * Creates a concurrency limited channel for per-endpoint limiting.
     * Metrics are not reported by this component per-endpoint, only by the per-endpoint queue.
     */
    static LimitedChannel createForEndpoint(Channel channel, String channelName, int uriIndex, Endpoint endpoint) {
        return new ConcurrencyLimitedChannel(
                channel,
                createLimiter(Behavior.ENDPOINT_LEVEL),
                new EndpointConcurrencyLimitedChannelInstrumentation(channelName, uriIndex, endpoint));
    }

    ConcurrencyLimitedChannel(
            Channel delegate,
            CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter,
            ConcurrencyLimitedChannelInstrumentation instrumentation) {
        this.delegate = new NeverThrowChannel(delegate);
        this.limiter = limiter;
        this.channelNameForLogging = instrumentation.channelNameForLogging();
    }

    static CautiousIncreaseAggressiveDecreaseConcurrencyLimiter createLimiter(Behavior behavior) {
        return new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(behavior);
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> maybePermit =
                limiter.acquire(limitEnforcement);
        if (maybePermit.isPresent()) {
            CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit permit = maybePermit.get();
            logPermitAcquired();
            ListenableFuture<Response> result = delegate.execute(endpoint, request);
            DialogueFutures.addDirectCallback(result, permit);
            return Optional.of(result);
        } else {
            logPermitRefused();
            return Optional.empty();
        }
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
        return "ConcurrencyLimitedChannel{delegate=" + delegate + ", name=" + channelNameForLogging + '}';
    }

    interface ConcurrencyLimitedChannelInstrumentation {

        String channelNameForLogging();
    }

    static final class HostConcurrencyLimitedChannelInstrumentation
            implements ConcurrencyLimitedChannelInstrumentation {

        private final String channelNameForLogging;

        HostConcurrencyLimitedChannelInstrumentation(
                String channelName,
                int uriIndex,
                CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter,
                TaggedMetricRegistry taggedMetrics) {
            if (uriIndex == 1) {
                throw new SafeIllegalArgumentException(
                        "uriIndex must be specified", SafeArg.of("channel-name", channelName));
            }
            channelNameForLogging = channelName + "{uriIndex=" + uriIndex + "}";
            DialogueConcurrencylimiterMetrics metrics = DialogueConcurrencylimiterMetrics.of(taggedMetrics);
            DialogueInternalWeakReducingGauge.getOrCreateDouble(
                    taggedMetrics,
                    metrics.max()
                            .channelName(channelName)
                            .hostIndex(Integer.toString(uriIndex))
                            .buildMetricName(),
                    CautiousIncreaseAggressiveDecreaseConcurrencyLimiter::getLimit,
                    doubleStream -> doubleStream.min().orElse(0D),
                    limiter);
            DialogueInternalWeakReducingGauge.getOrCreate(
                    taggedMetrics,
                    metrics.inFlight()
                            .channelName(channelName)
                            .hostIndex(Integer.toString(uriIndex))
                            .buildMetricName(),
                    CautiousIncreaseAggressiveDecreaseConcurrencyLimiter::getInflight,
                    LongStream::sum,
                    limiter);
        }

        @Override
        public String channelNameForLogging() {
            return channelNameForLogging;
        }
    }

    static final class EndpointConcurrencyLimitedChannelInstrumentation
            implements ConcurrencyLimitedChannelInstrumentation {

        private final String channelNameForLogging;

        EndpointConcurrencyLimitedChannelInstrumentation(String channelName, int uriIndex, Endpoint endpoint) {
            channelNameForLogging = channelName + "{uriIndex=" + uriIndex + ", endpoint=" + endpoint.serviceName() + '.'
                    + endpoint.endpointName() + "}";
        }

        @Override
        public String channelNameForLogging() {
            return channelNameForLogging;
        }
    }
}
