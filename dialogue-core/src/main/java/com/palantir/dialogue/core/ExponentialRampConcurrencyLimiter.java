/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.util.concurrent.AtomicDouble;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Experimental concurrency limiter that adds an initial exponential ramp phase in front of the AIMD behavior of
 * {@link CautiousIncreaseAggressiveDecreaseConcurrencyLimiter}.
 *
 * Deliberately not applied to sticky channels, which keep AIMD: {@link Behavior#STICKY} suppresses the server
 * congestion signals (QoS/5xx/IOException) this ramp relies on.
 */
final class ExponentialRampConcurrencyLimiter implements ConcurrencyLimiter {

    private static final SafeLogger log = SafeLoggerFactory.get(ExponentialRampConcurrencyLimiter.class);
    private static final double INITIAL_LIMIT = 20;
    private static final double BACKOFF_RATIO = .9D;
    private static final double MIN_LIMIT = 1;
    private static final double MAX_LIMIT = 1_000_000D;

    private final AtomicDouble limit = new AtomicDouble(INITIAL_LIMIT);

    /**
     * Whether the limiter is still in its initial exponential ramp. Starts true and switches to false on the first
     * {@code dropped()} signal, after which the limiter behaves as pure AIMD for the rest of its life.
     */
    private volatile boolean inExponentialRamp = true;

    private final AtomicInteger inFlight = new AtomicInteger();

    private final Behavior behavior;

    // Used strictly for providing the channel name in debug logs.
    // Volatile because it may be set from a different thread than the one emitting the log line.
    private volatile String channelNameForLogging = "unknown";

    ExponentialRampConcurrencyLimiter(Behavior behavior) {
        this.behavior = behavior;
    }

    @Override
    public void setChannelNameForLogging(String value) {
        this.channelNameForLogging = value;
    }

    @Override
    @Nullable // avoiding Optional because this method is on the hot path
    public Permit acquire(LimitEnforcement limitEnforcement) {
        AtomicInteger localInFlight = inFlight;

        int currentLimit = (int) getLimit();
        while (true) {
            int currentInFlight = localInFlight.get();
            if (limitEnforcement.enforceLimits() && currentInFlight >= currentLimit) {
                return null;
            }

            int newInFlight = currentInFlight + 1;
            if (inFlight.compareAndSet(currentInFlight, newInFlight)) {
                return new Permit(newInFlight);
            }
        }
    }

    final class Permit implements Behavior.PermitControl, ConcurrencyLimiter.Permit {
        private final int inFlightSnapshot;

        Permit(int inFlightSnapshot) {
            this.inFlightSnapshot = inFlightSnapshot;
        }

        @Override
        public void onSuccess(@Nullable Response result) {
            if (result != null) {
                behavior.onSuccess(result, this);
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            behavior.onFailure(throwable, this);
        }

        @Override
        public void ignore() {
            inFlight.decrementAndGet();
        }

        @Override
        public void dropped() {
            inFlight.decrementAndGet();
            double newLimit = decreaseLimit();
            if (log.isDebugEnabled()) {
                log.debug(
                        "Decreasing limit on {} to {}",
                        SafeArg.of("channel", channelNameForLogging),
                        SafeArg.of("newLimit", newLimit),
                        SafeArg.of("behavior", behavior),
                        SafeArg.of("inFlightSnapshot", inFlightSnapshot));
            }
        }

        @Override
        public void success() {
            inFlight.decrementAndGet();
            double newLimit = increaseLimit(inFlightSnapshot);
            if (log.isDebugEnabled()) {
                log.debug(
                        "Increasing limit on {} to {}",
                        SafeArg.of("channel", channelNameForLogging),
                        SafeArg.of("newLimit", newLimit),
                        SafeArg.of("behavior", behavior),
                        SafeArg.of("inFlightSnapshot", inFlightSnapshot));
            }
        }
    }

    private double increaseLimit(int inFlightSnapshot) {
        AtomicDouble localLimit = limit;
        while (true) {
            double snapshot = localLimit.get();
            if (inFlightSnapshot < Math.floor(snapshot * BACKOFF_RATIO)) {
                return snapshot;
            }
            double increment = inExponentialRamp ? 1D : 1D / snapshot;
            double newLimit = Math.min(MAX_LIMIT, snapshot + increment);
            if (localLimit.compareAndSet(snapshot, newLimit)) {
                return newLimit;
            }
        }
    }

    private double decreaseLimit() {
        // Publish the phase transition before the reduced limit. A concurrent success may either complete its
        // exponential-ramp increase before this decrease or observe AIMD mode, but it cannot apply an exponential-ramp
        // increase to the already-reduced limit.
        inExponentialRamp = false;

        AtomicDouble localLimit = limit;
        double newLimit;
        while (true) {
            double snapshot = localLimit.get();
            newLimit = Math.max(MIN_LIMIT, Math.floor(snapshot * BACKOFF_RATIO));
            if (localLimit.compareAndSet(snapshot, newLimit)) {
                break;
            }
        }
        return newLimit;
    }

    @Override
    public double getLimit() {
        return limit.get();
    }

    @VisibleForTesting
    boolean isInExponentialRamp() {
        return inExponentialRamp;
    }

    @Override
    public int getInflight() {
        return inFlight.get();
    }

    @Override
    public String toString() {
        return "ExponentialRampConcurrencyLimiter{limit=" + limit + ", inExponentialRamp=" + inExponentialRamp
                + ", inFlight=" + inFlight + '}';
    }
}
