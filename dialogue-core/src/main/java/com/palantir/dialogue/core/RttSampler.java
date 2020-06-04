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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgent.Agent;
import com.palantir.conjure.java.api.config.service.UserAgents;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.io.Closeable;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RttSampler {
    private static final Logger log = LoggerFactory.getLogger(RttSampler.class);
    private static final String USER_AGENT =
            UserAgents.format(UserAgent.of(Agent.of(RttEndpoint.INSTANCE.serviceName(), RttEndpoint.INSTANCE.version()))
                    .addAgent(UserAgentEndpointChannel.DIALOGUE_AGENT));

    private final ImmutableList<LimitedChannel> channels;
    private final RttMeasurement[] rtts;
    private final RttMeasurementRateLimiter rateLimiter;
    private final Ticker clock;

    RttSampler(ImmutableList<LimitedChannel> channels, Ticker clock) {
        this.channels = channels;
        this.rateLimiter = new RttMeasurementRateLimiter(clock);
        this.clock = clock;
        this.rtts = IntStream.range(0, channels.size())
                .mapToObj(_i -> new RttMeasurement())
                .toArray(RttMeasurement[]::new);
    }

    /**
     * Latency (rtt) is measured in nanos, which is a tricky unit to include in our 'score' because adding
     * it would dominate all the other data (which has the unit of 'num requests'). To avoid the need for a
     * conversion fudge-factor, we instead figure out where each rtt lies on the spectrum from bestRttNanos
     * to worstRttNanos, with 0 being best and 1 being worst. This ensures that if several nodes are all
     * within the same AZ and can return in ~1 ms but others return in ~5ms, the 1ms nodes will all have
     * a similar rttScore (near zero). Note, this can only be computed when we have all the snapshots in
     * front of us.
     */
    float[] computeRttSpectrums() {
        long bestRttNanos = Long.MAX_VALUE;
        long worstRttNanos = 0;

        // first we take a snapshot of all channels' RTT
        OptionalLong[] snapshots = new OptionalLong[rtts.length];
        for (int i = 0; i < rtts.length; i++) {
            OptionalLong rtt = rtts[i].getRttNanos();
            snapshots[i] = rtt;

            if (rtt.isPresent()) {
                bestRttNanos = Math.min(bestRttNanos, rtt.getAsLong());
                worstRttNanos = Math.max(worstRttNanos, rtt.getAsLong());
            }
        }

        // given the best & worst values, we can then compute the spectrums
        float[] spectrums = new float[rtts.length];
        long rttRange = worstRttNanos - bestRttNanos;
        if (rttRange <= 0) {
            return spectrums;
        }

        for (int i = 0; i < channels.size(); i++) {
            OptionalLong rtt = snapshots[i];
            float rttSpectrum = rtt.isPresent() ? ((float) (rtt.getAsLong() - bestRttNanos)) / rttRange : 0;
            if (rttSpectrum < 0f || rttSpectrum > 1f) {
                log.warn(
                        "rttSpectrum should be between 0 and 1",
                        SafeArg.of("value", rttSpectrum),
                        SafeArg.of("hostIndex", i));
                rttSpectrum = Math.min(1f, Math.max(0f, rttSpectrum));
            }
            spectrums[i] = rttSpectrum;
        }

        return spectrums;
    }

    /**
     * Non-blocking - should return pretty much instantly.
     */
    void maybeSampleRtts() {
        Optional<RttMeasurementPermit> maybePermit = rateLimiter.tryAcquire();
        if (!maybePermit.isPresent()) {
            return;
        }

        Request rttRequest = Request.builder()
                // necessary as we've already gone through the UserAgentEndpointChannel
                .putHeaderParams("user-agent", USER_AGENT)
                .build();

        List<ListenableFuture<Long>> futures = IntStream.range(0, channels.size())
                .mapToObj(i -> {
                    long before = clock.read();
                    return channels.get(i)
                            .maybeExecute(RttEndpoint.INSTANCE, rttRequest)
                            .map(future -> Futures.transform(
                                    future,
                                    response -> {
                                        long durationNanos = clock.read() - before;
                                        rtts[i].addMeasurement(durationNanos);
                                        response.close();
                                        return durationNanos;
                                    },
                                    MoreExecutors.directExecutor()))
                            .orElseGet(() -> Futures.immediateFuture(Long.MAX_VALUE));
                })
                .collect(ImmutableList.toImmutableList());

        DialogueFutures.addDirectCallback(Futures.allAsList(futures), new FutureCallback<List<Long>>() {
            @Override
            public void onSuccess(List<Long> result) {
                maybePermit.get().close();

                if (log.isDebugEnabled()) {
                    List<Long> millis =
                            result.stream().map(TimeUnit.NANOSECONDS::toMillis).collect(Collectors.toList());
                    long[] best = Arrays.stream(rtts)
                            .mapToLong(rtt -> rtt.getRttNanos().orElse(Long.MAX_VALUE))
                            .toArray();
                    log.debug(
                            "RTTs {} {} {} {}",
                            SafeArg.of("nanos", result),
                            SafeArg.of("millis", millis),
                            SafeArg.of("best", Arrays.toString(best)),
                            UnsafeArg.of("channels", channels));
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                maybePermit.get().close();
                log.info("Failed to sample RTT for channels", throwable);
            }
        });
    }

    @VisibleForTesting
    enum RttEndpoint implements Endpoint {
        INSTANCE;

        @Override
        public void renderPath(Map<String, String> _params, UrlBuilder _url) {}

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.OPTIONS;
        }

        @Override
        public String serviceName() {
            return "RttSampler";
        }

        @Override
        public String endpointName() {
            return "rtt";
        }

        @Override
        public String version() {
            return UserAgentEndpointChannel.dialogueVersion();
        }
    }

    /**
     * Always returns the *minimum* value from the last few samples, so that we exclude slow calls that might include
     * TLS handshakes.
     */
    @VisibleForTesting
    @ThreadSafe
    static final class RttMeasurement {
        private static final int NUM_MEASUREMENTS = 5;

        private final long[] samples;
        private volatile long bestRttNanos = Long.MAX_VALUE;

        RttMeasurement() {
            samples = new long[NUM_MEASUREMENTS];
            Arrays.fill(samples, Long.MAX_VALUE);
        }

        public OptionalLong getRttNanos() {
            return bestRttNanos == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(bestRttNanos);
        }

        synchronized void addMeasurement(long newMeasurement) {
            Preconditions.checkArgument(newMeasurement > 0, "Must be greater than zero");
            Preconditions.checkArgument(newMeasurement < Long.MAX_VALUE, "Must be less than MAX_VALUE");

            if (samples[0] == Long.MAX_VALUE) {
                Arrays.fill(samples, newMeasurement);
                bestRttNanos = newMeasurement;
            } else {
                System.arraycopy(samples, 1, samples, 0, NUM_MEASUREMENTS - 1);
                samples[NUM_MEASUREMENTS - 1] = newMeasurement;
                bestRttNanos = Arrays.stream(samples).min().getAsLong();
            }
        }

        @Override
        public String toString() {
            return "RttMeasurement{" + "bestRttNanos=" + bestRttNanos + ", samples=" + Arrays.toString(samples) + '}';
        }
    }

    private static final class RttMeasurementRateLimiter {
        private static final long BETWEEN_SAMPLES = Duration.ofSeconds(1).toNanos();

        private final Ticker clock;
        private final AtomicBoolean currentlySampling = new AtomicBoolean(false);
        private volatile long lastMeasured = 0;

        @SuppressWarnings("UnnecessaryLambda") // just let me avoid allocations
        private final RttMeasurementPermit finishedSampling = () -> currentlySampling.set(false);

        private RttMeasurementRateLimiter(Ticker clock) {
            this.clock = clock;
        }

        /**
         * The RttSamplePermit ensures that if a server black-holes one of our OPTIONS requests, we don't kick off
         * more and more and more requests and eventually exhaust a threadpool. Permit is released in a future callback.
         */
        Optional<RttMeasurementPermit> tryAcquire() {
            if (lastMeasured + BETWEEN_SAMPLES > clock.read()) {
                return Optional.empty();
            }

            if (!currentlySampling.get() && currentlySampling.compareAndSet(false, true)) {
                lastMeasured = clock.read();
                return Optional.of(finishedSampling);
            } else {
                log.warn("Wanted to sample RTTs but an existing sample was still in progress");
                return Optional.empty();
            }
        }
    }

    private interface RttMeasurementPermit extends Closeable {
        @Override
        void close();
    }
}
