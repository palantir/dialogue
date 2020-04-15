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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.function.Function;

/**
 * A channel that monitors the successes and failures of requests in order to determine the number of concurrent
 * requests allowed to a particular channel. If the channel's concurrency limit has been reached, the
 * {@link #maybeExecute} method returns empty.
 */
final class ConcurrencyLimitedChannel implements LimitedChannel {
    static final int INITIAL_LIMIT = 20;

    private final Meter limitedMeter;
    private final LimitedChannel delegate;
    private final AimdConcurrencyLimiter limiter;

    ConcurrencyLimitedChannel(
            LimitedChannel delegate,
            AimdConcurrencyLimiter limiter,
            String channelName,
            int uriIndex,
            TaggedMetricRegistry taggedMetrics) {
        this.delegate = new NeverThrowLimitedChannel(delegate);
        this.limitedMeter = DialogueClientMetrics.of(taggedMetrics)
                .limited()
                .channelName(channelName)
                .reason(getClass().getSimpleName())
                .build();
        this.limiter = limiter;

        weakGauge(
                taggedMetrics,
                MetricName.builder()
                        .safeName("dialogue.concurrencylimiter.utilization")
                        .putSafeTags("channel-name", channelName)
                        .putSafeTags("hostIndex", Integer.toString(uriIndex))
                        .build(),
                this,
                ConcurrencyLimitedChannel::getUtilization);
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

    static AimdConcurrencyLimiter createLimiter() {
        return new AimdConcurrencyLimiter();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        Optional<AimdConcurrencyLimiter.Permit> maybePermit = limiter.acquire();
        if (maybePermit.isPresent()) {
            AimdConcurrencyLimiter.Permit permit = maybePermit.get();
            Optional<ListenableFuture<Response>> result = delegate.maybeExecute(endpoint, request);
            if (result.isPresent()) {
                DialogueFutures.addDirectCallback(result.get(), permit);
            } else {
                permit.ignore();
            }
            return result;
        } else {
            limitedMeter.mark();
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "ConcurrencyLimitedChannel{" + delegate + '}';
    }

    private double getUtilization() {
        double inflight = limiter.getInflight();
        double limit = limiter.getLimit();
        return inflight / limit; // minLimit is 1 so we should never get NaN from this
    }

    private int getMax() {
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
