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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A channel that monitors the successes and failures of requests in order to determine the number of concurrent
 * requests allowed to a particular channel. If the channel's concurrency limit has been reached, the
 * {@link EndpointMaybeChannel#maybeExecute} method returns empty.
 */
final class ConcurrencyLimitedChannel implements EndpointMaybeChannelFactory {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimitedChannel.class);

    private final Meter limitedMeter;
    private final EndpointChannelFactory delegate;
    private final CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter;

    static EndpointMaybeChannelFactory create(Config cf, EndpointChannelFactory channel, int uriIndex) {
        ClientConfiguration.ClientQoS clientQoS = cf.clientConf().clientQoS();
        switch (clientQoS) {
            case ENABLED:
                TaggedMetricRegistry metrics = cf.clientConf().taggedMetricRegistry();
                return new ConcurrencyLimitedChannel(channel, createLimiter(), cf.channelName(), uriIndex, metrics);
            case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                // TODO(dfox): extract this?
                return endpoint -> {
                    EndpointChannel delegate = channel.endpoint(endpoint);
                    return request -> Optional.of(delegate.execute(request));
                };
        }
        throw new SafeIllegalStateException(
                "Encountered unknown client QoS configuration", SafeArg.of("ClientQoS", clientQoS));
    }

    ConcurrencyLimitedChannel(
            EndpointChannelFactory delegate,
            CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter,
            String channelName,
            int uriIndex,
            TaggedMetricRegistry taggedMetrics) {
        this.delegate = delegate;
        this.limitedMeter = DialogueClientMetrics.of(taggedMetrics)
                .limited()
                .channelName(channelName)
                .reason(getClass().getSimpleName())
                .build();
        this.limiter = limiter;
        Preconditions.checkArgument(
                uriIndex != -1, "uriIndex must be specified", SafeArg.of("channel-name", channelName));
        weakGauge(
                taggedMetrics,
                MetricName.builder()
                        .safeName("dialogue.concurrencylimiter.max")
                        .putSafeTags("channel-name", channelName)
                        .putSafeTags("hostIndex", Integer.toString(uriIndex))
                        .build(),
                this,
                ConcurrencyLimitedChannel::getMax);
    }

    static CautiousIncreaseAggressiveDecreaseConcurrencyLimiter createLimiter() {
        return new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter();
    }

    @Override
    public EndpointMaybeChannel endpoint(Endpoint endpoint) {
        EndpointChannel proceed = delegate.endpoint(endpoint);

        return new EndpointMaybeChannel() {
            @Override
            public Optional<ListenableFuture<Response>> maybeExecute(Request request) {
                Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> maybePermit = limiter.acquire();
                if (maybePermit.isPresent()) {
                    CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit permit = maybePermit.get();
                    logPermitAcquired();
                    ListenableFuture<Response> result = proceed.execute(request);
                    DialogueFutures.addDirectCallback(result, permit);
                    return Optional.of(result);
                } else {
                    logPermitRefused();
                    limitedMeter.mark();
                    return Optional.empty();
                }
            }

            @Override
            public String toString() {
                return "ConcurrerencyLimitdChannel.EndpointMaybeChannel{" + proceed + '}';
            }
        };
    }

    private void logPermitAcquired() {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Sending {}/{}",
                    SafeArg.of("inflight", limiter.getInflight()),
                    SafeArg.of("max", limiter.getLimit()));
        }
    }

    private void logPermitRefused() {
        if (log.isDebugEnabled()) {
            log.debug("Limited {}", SafeArg.of("max", limiter.getLimit()));
        }
    }

    @Override
    public String toString() {
        return "ConcurrencyLimitedChannel{" + delegate + '}';
    }

    private double getMax() {
        return limiter.getLimit();
    }

    /**
     * Creates a gauge that removes itself when the {@code instance} has been garbage-collected.
     * We need this to ensure that ConcurrencyLimitedChannels can be GC'd when urls live-reload, otherwise metric
     * registries could keep around dangling references.
     */
    private static <T> void weakGauge(
            TaggedMetricRegistry registry, MetricName metricName, T instance, Function<T, Number> gaugeFunction) {
        registry.registerWithReplacement(metricName, new Gauge<Number>() {
            private final WeakReference<T> weakReference = new WeakReference<>(instance);

            @Override
            public Number getValue() {
                T value = weakReference.get();
                if (value != null) {
                    return gaugeFunction.apply(value);
                } else {
                    registry.remove(metricName); // registry must be tolerant to concurrent modification
                    return 0;
                }
            }
        });
    }
}
