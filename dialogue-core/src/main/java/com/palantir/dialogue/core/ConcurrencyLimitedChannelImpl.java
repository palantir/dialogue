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
import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A channel that monitors the successes and failures of requests in order to determine the number of concurrent
 * requests allowed to a particular channel. If the channel's concurrency limit has been reached, the {@link
 * #maybeExecute} method returns empty.
 */
final class ConcurrencyLimitedChannelImpl implements ConcurrencyLimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimitedChannelImpl.class);

    private final LimitedChannel delegate;
    private final Meter limitedMeter;
    private final String channelName;
    private final int uriIndex;
    private final CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter;

    private final Timer debugTimer = new Timer();

    ConcurrencyLimitedChannelImpl(
            LimitedChannel delegate,
            CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter,
            String channelName,
            int uriIndex,
            TaggedMetricRegistry taggedMetrics) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.channelName = channelName;
        this.uriIndex = uriIndex;
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
                DialogueConcurrencylimiterMetrics.of(taggedMetrics)
                        .max()
                        .channelName(channelName)
                        .hostIndex(Integer.toString(uriIndex))
                        .buildMetricName(),
                this,
                instance -> instance.getMax().getAsDouble());
    }

    static CautiousIncreaseAggressiveDecreaseConcurrencyLimiter createLimiter() {
        return new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> maybePermit = limiter.acquire();
        if (maybePermit.isPresent()) {
            CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit permit = maybePermit.get();
            logPermitAcquired();
            long before = System.nanoTime();
            Optional<ListenableFuture<Response>> result = delegate.maybeExecute(endpoint, request);
            if (result.isPresent()) {
                DialogueFutures.addDirectCallback(result.get(), permit);

                if (log.isDebugEnabled()) {
                    DialogueFutures.addDirectCallback(result.get(), new FutureCallback<Response>() {
                        @Override
                        public void onSuccess(Response result) {
                            if (result.code() / 100 == 2) {
                                debugTimer.update(System.nanoTime() - before, TimeUnit.NANOSECONDS);
                            }
                        }

                        @Override
                        public void onFailure(Throwable _throwable) {}
                    });
                }
            } else {
                permit.ignore();
            }
            return result;
        } else {
            logPermitRefused();
            limitedMeter.mark();
            return Optional.empty();
        }
    }

    private void logPermitAcquired() {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Sending {}/{} qpsEstimate={}/{} ({} {})",
                    SafeArg.of("inflight", limiter.getInflight()),
                    SafeArg.of("max", limiter.getLimit()),
                    SafeArg.of("clientQps", Math.round(debugTimer.getMeanRate())),
                    SafeArg.of(
                            "inferredServerQpsLimit",
                            Math.round(1000_000_000L
                                    * limiter.getLimit()
                                    / debugTimer.getSnapshot().getMean())),
                    SafeArg.of("channelName", channelName),
                    SafeArg.of("hostIndex", uriIndex));
        }
    }

    private void logPermitRefused() {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Limited {} ({} {})",
                    SafeArg.of("max", limiter.getLimit()),
                    SafeArg.of("channelName", channelName),
                    SafeArg.of("hostIndex", uriIndex));
        }
    }

    @Override
    public String toString() {
        return "ConcurrencyLimitedChannel{" + delegate + '}';
    }

    @Override
    public OptionalDouble getMax() {
        return OptionalDouble.of(limiter.getLimit());
    }

    @Override
    public int getInflight() {
        return limiter.getInflight();
    }

    /**
     * Creates a gauge that removes itself when the {@code instance} has been garbage-collected. We need this to ensure
     * that ConcurrencyLimitedChannels can be GC'd when urls live-reload, otherwise metric registries could keep around
     * dangling references.
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
