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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.ConcurrencyLimiter.Permit;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.stream.LongStream;
import org.jspecify.annotations.Nullable;

/**
 * A channel that monitors the successes and failures of requests in order to determine the number of concurrent
 * requests allowed to a particular channel. If the channel's concurrency limit has been reached, the
 * {@link LimitedChannel#maybeExecute} method returns empty.
 */
final class ConcurrencyLimitedChannel implements LimitedChannel {
    private static final SafeLogger log = SafeLoggerFactory.get(ConcurrencyLimitedChannel.class);

    @VisibleForTesting
    static final ChannelState.Key<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter> HOST_SPECIFIC_STATE_KEY =
            new ChannelState.Key<>(
                    CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.class,
                    () -> new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(Behavior.HOST_LEVEL));

    @VisibleForTesting
    static final ChannelState.Key<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter> ENDPOINT_SPECIFIC_STATE_KEY =
            new ChannelState.Key<>(
                    CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.class,
                    () -> new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(Behavior.ENDPOINT_LEVEL));

    // Experimental slow-start limiters, selected by ConcurrencyLimiters#slowStartEnabled. Kept under separate state
    // keys so flipping the flag picks up the alternate implementation without clobbering the default limiter's state.
    @VisibleForTesting
    static final ChannelState.Key<SlowStartConcurrencyLimiter> HOST_SPECIFIC_SLOW_START_STATE_KEY =
            new ChannelState.Key<>(
                    SlowStartConcurrencyLimiter.class, () -> new SlowStartConcurrencyLimiter(Behavior.HOST_LEVEL));

    @VisibleForTesting
    static final ChannelState.Key<SlowStartConcurrencyLimiter> ENDPOINT_SPECIFIC_SLOW_START_STATE_KEY =
            new ChannelState.Key<>(
                    SlowStartConcurrencyLimiter.class, () -> new SlowStartConcurrencyLimiter(Behavior.ENDPOINT_LEVEL));

    private final NeverThrowChannel delegate;
    private final ConcurrencyLimiter limiter;
    private final String channelNameForLogging;

    static LimitedChannel createForHost(Config cf, Channel channel, int uriIndex, ChannelState hostSpecificState) {
        TaggedMetricRegistry metrics = cf.clientConf().taggedMetricRegistry();
        ConcurrencyLimiter limiter = slowStartEnabled(cf)
                ? hostSpecificState.getState(HOST_SPECIFIC_SLOW_START_STATE_KEY)
                : hostSpecificState.getState(HOST_SPECIFIC_STATE_KEY);
        ConcurrencyLimitedChannelInstrumentation instrumentation =
                new HostConcurrencyLimitedChannelInstrumentation(cf.channelName(), uriIndex, limiter, metrics);
        limiter.setChannelNameForLogging(instrumentation.channelNameForLogging());
        return new ConcurrencyLimitedChannel(channel, limiter, instrumentation);
    }

    /**
     * Creates a concurrency limited channel for per-endpoint limiting.
     * Metrics are not reported by this component per-endpoint, only by the per-endpoint queue.
     */
    static LimitedChannel createForEndpoint(
            Channel channel,
            String channelName,
            int uriIndex,
            Endpoint endpoint,
            ChannelState endpointChannelState,
            boolean useSlowStart) {
        ConcurrencyLimiter limiter = useSlowStart
                ? endpointChannelState.getState(ENDPOINT_SPECIFIC_SLOW_START_STATE_KEY)
                : endpointChannelState.getState(ENDPOINT_SPECIFIC_STATE_KEY);
        ConcurrencyLimitedChannelInstrumentation instrumentation =
                new EndpointConcurrencyLimitedChannelInstrumentation(channelName, uriIndex, endpoint);
        limiter.setChannelNameForLogging(instrumentation.channelNameForLogging());
        return new ConcurrencyLimitedChannel(channel, limiter, instrumentation);
    }

    static boolean slowStartEnabled(Config cf) {
        return cf.concurrencyLimiterSlowStart().orElseGet(ConcurrencyLimiters::slowStartEnabled);
    }

    ConcurrencyLimitedChannel(
            Channel delegate, ConcurrencyLimiter limiter, ConcurrencyLimitedChannelInstrumentation instrumentation) {
        this.delegate = new NeverThrowChannel(delegate);
        this.limiter = limiter;
        this.channelNameForLogging = instrumentation.channelNameForLogging();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        @Nullable Permit maybePermit = limiter.acquire(limitEnforcement);
        if (maybePermit != null) {
            Permit permit = maybePermit;
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
                    SafeArg.of("inflight", limiter.getInFlight()),
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
                String channelName, int uriIndex, ConcurrencyLimiter limiter, TaggedMetricRegistry taggedMetrics) {
            if (uriIndex == -1) {
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
                    ConcurrencyLimiter::getLimit,
                    doubleStream -> doubleStream.min().orElse(0D),
                    limiter);
            DialogueInternalWeakReducingGauge.getOrCreate(
                    taggedMetrics,
                    metrics.inFlight()
                            .channelName(channelName)
                            .hostIndex(Integer.toString(uriIndex))
                            .buildMetricName(),
                    ConcurrencyLimiter::getInFlight,
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
