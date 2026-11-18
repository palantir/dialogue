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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines.Enforcement;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Randomly fails a small fraction of deadline-enforced requests with a {@link DeadlineExpiredException}, so that
 * services can exercise their deadline handling outside of production. Disabled unless the
 * {@value DialogueEnvironmentVariables#INJECT_DEADLINE_FAILURES} environment variable is set.
 *
 * <p>Two settings bound the failure rate, and the observed rate is the lower of the two: a probability, and a minimum
 * interval between failures on a single channel. See {@link DeadlineFailureInjectionConfiguration} for the format.
 *
 * <p>Note that {@link DeadlineExpiredException} is sealed, so injected failures are indistinguishable by type from
 * genuine ones. The warning logged alongside each injected failure is what identifies it as synthetic.
 */
final class DeadlineFailureInjectionChannel implements Channel {
    private static final SafeLogger log = SafeLoggerFactory.get(DeadlineFailureInjectionChannel.class);

    private final Channel delegate;
    private final String channelName;
    private final int oneInEvery;
    private final long minimumFailureIntervalNanos;
    private final IntSupplier random;
    private final Ticker ticker;
    private final AtomicLong nextFailureNanos = new AtomicLong(Long.MIN_VALUE);

    static Channel wrapDelegateIfEnabled(Channel delegate, @Safe String channelName, Enforcement enforcement) {
        return wrapDelegateIfEnabled(
                delegate,
                channelName,
                enforcement,
                DeadlineFailureInjectionConfiguration.fromEnvironment(),
                // The bound is the configured rate, so a value of zero occurs once every oneInEvery requests.
                bound -> ThreadLocalRandom.current().nextInt(bound),
                Ticker.systemTicker());
    }

    static Channel wrapDelegateIfEnabled(
            Channel delegate,
            @Safe String channelName,
            Enforcement enforcement,
            Optional<DeadlineFailureInjectionConfiguration> configuration,
            IntUnaryOperator boundedRandom,
            Ticker ticker) {
        if (configuration.isEmpty() || enforcement != Enforcement.ENFORCE) {
            return delegate;
        }
        int oneInEvery = configuration.get().oneInEvery();
        Duration minimumFailureInterval = configuration.get().minimumFailureInterval();
        IntSupplier random = () -> boundedRandom.applyAsInt(oneInEvery);
        log.info(
                "Deadline failure injection is enabled. A fraction of requests on this channel will be failed with a "
                        + "synthetic deadline expiration; these failures are deliberate and do not indicate a problem",
                SafeArg.of("channelName", channelName),
                SafeArg.of("oneInEvery", oneInEvery),
                SafeArg.of("minimumFailureInterval", minimumFailureInterval));
        return new DeadlineFailureInjectionChannel(
                delegate, channelName, oneInEvery, minimumFailureInterval, random, ticker);
    }

    private DeadlineFailureInjectionChannel(
            Channel delegate,
            @Safe String channelName,
            int oneInEvery,
            Duration minimumFailureInterval,
            IntSupplier random,
            Ticker ticker) {
        this.delegate = delegate;
        this.channelName = channelName;
        this.oneInEvery = oneInEvery;
        this.minimumFailureIntervalNanos = minimumFailureInterval.toNanos();
        this.random = random;
        this.ticker = ticker;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        if (random.getAsInt() == 0 && acquireFailurePermit()) {
            log.warn(
                    "Failing this request with a synthetic deadline expiration because deadline failure injection is "
                            + "enabled. No deadline actually expired and nothing is wrong with the client, server, or "
                            + "network; this request was failed deliberately to exercise deadline handling. Unset the "
                            + "environment variable to disable injection",
                    SafeArg.of("environmentVariable", DialogueEnvironmentVariables.INJECT_DEADLINE_FAILURES),
                    SafeArg.of("channelName", channelName),
                    SafeArg.of("service", endpoint.serviceName()),
                    SafeArg.of("endpoint", endpoint.endpointName()),
                    SafeArg.of("oneInEvery", oneInEvery));
            return Futures.immediateFailedFuture(DeadlineExpiredException.external());
        }
        return delegate.execute(endpoint, request);
    }

    private boolean acquireFailurePermit() {
        if (minimumFailureIntervalNanos == 0) {
            // The cap is disabled, so the configured rate alone determines how often failures are injected.
            return true;
        }
        long now = ticker.read();
        long nextFailure = nextFailureNanos.get();
        if (now < nextFailure) {
            return false;
        }
        long newNextFailure = now + minimumFailureIntervalNanos;
        return nextFailureNanos.compareAndSet(nextFailure, newNextFailure);
    }
}
