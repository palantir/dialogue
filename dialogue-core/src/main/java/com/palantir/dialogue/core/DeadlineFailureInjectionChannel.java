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
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

final class DeadlineFailureInjectionChannel implements Channel {
    private static final SafeLogger log = SafeLoggerFactory.get(DeadlineFailureInjectionChannel.class);
    private static final int DEADLINE_FAILURE_SELECTION_BOUND = 100_000;
    private static final long MINIMUM_FAILURE_INTERVAL_NANOS =
            Duration.ofMinutes(15).toNanos();

    private final Channel delegate;
    private final IntSupplier random;
    private final Ticker ticker;
    private final AtomicLong nextFailureNanos = new AtomicLong(Long.MIN_VALUE);

    static Channel wrapDelegateIfEnabled(Channel delegate, Enforcement enforcement) {
        return wrapDelegateIfEnabled(
                delegate,
                enforcement,
                isDeadlineFailureInjectionEnabled(),
                () -> ThreadLocalRandom.current().nextInt(DEADLINE_FAILURE_SELECTION_BOUND),
                Ticker.systemTicker());
    }

    static Channel wrapDelegateIfEnabled(
            Channel delegate,
            Enforcement enforcement,
            boolean deadlineFailureInjectionEnabled,
            IntSupplier random,
            Ticker ticker) {
        if (!deadlineFailureInjectionEnabled || enforcement != Enforcement.ENFORCE) {
            return delegate;
        }
        return new DeadlineFailureInjectionChannel(delegate, random, ticker);
    }

    private static boolean isDeadlineFailureInjectionEnabled() {
        return "true".equalsIgnoreCase(System.getenv(DialogueEnvironmentVariables.INJECT_DEADLINE_FAILURES));
    }

    private DeadlineFailureInjectionChannel(Channel delegate, IntSupplier random, Ticker ticker) {
        this.delegate = delegate;
        this.random = random;
        this.ticker = ticker;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        if (random.getAsInt() == 0 && acquireFailurePermit()) {
            log.warn("Probabilistically failing this request with a Deadline expiration exception.");
            return Futures.immediateFailedFuture(DeadlineExpiredException.external());
        }
        return delegate.execute(endpoint, request);
    }

    private boolean acquireFailurePermit() {
        long now = ticker.read();
        long nextFailure = nextFailureNanos.get();
        if (now < nextFailure) {
            return false;
        }
        long newNextFailure = now + MINIMUM_FAILURE_INTERVAL_NANOS;
        return nextFailureNanos.compareAndSet(nextFailure, newNextFailure);
    }
}
