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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * An {@link EndpointChannel} that enforces deadlines propagated via {@link Deadlines}.
 *
 * <p>Sits above {@link RetryingChannel} in the pipeline, so the deadline covers queue time,
 * all retries, and wire time. When the deadline expires, the caller's future is failed with
 * {@link DeadlineExpiredException} and the delegate future is cancelled (freeing the connection).
 * The scheduled task is cancelled when the delegate future completes (first response byte /
 * headers received).
 *
 * <p>{@link DeadlineExpiredException} extends {@link RuntimeException}, not {@link java.io.IOException},
 * so it is not retried by {@link RetryingChannel}.
 */
final class DeadlineEnforcingChannel implements EndpointChannel {

    private static final SafeLogger log = SafeLoggerFactory.get(DeadlineEnforcingChannel.class);

    // Clamp to avoid ArithmeticException from Duration.toNanos() on absurdly large deadlines
    private static final long MAX_DEADLINE_NANOS = TimeUnit.DAYS.toNanos(1);

    private final EndpointChannel delegate;
    private final ScheduledExecutorService scheduler;

    DeadlineEnforcingChannel(EndpointChannel delegate, ScheduledExecutorService scheduler) {
        this.delegate = delegate;
        this.scheduler = scheduler;
    }

    static EndpointChannel create(Config cf, EndpointChannel delegate) {
        return new DeadlineEnforcingChannel(delegate, cf.scheduler());
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        Optional<Duration> remainingDeadline = Deadlines.getRemainingDeadline();
        if (remainingDeadline.isEmpty()) {
            return delegate.execute(request);
        }

        long remainingNanos = clampNanos(remainingDeadline.get());
        if (remainingNanos <= 0) {
            log.info(
                    "Deadline already expired before request execution",
                    SafeArg.of("remainingDeadline", remainingDeadline.get()));
            return Futures.immediateFailedFuture(DeadlineExpiredException.internal());
        }

        SettableFuture<Response> callerFuture = SettableFuture.create();
        ListenableFuture<Response> delegateFuture = delegate.execute(request);

        ScheduledFuture<?> deadlineTask = scheduler.schedule(
                () -> {
                    if (callerFuture.setException(DeadlineExpiredException.internal())) {
                        log.info(
                                "Deadline expired, cancelling in-flight request",
                                SafeArg.of("remainingNanos", remainingNanos));
                        // Cancel the delegate to free the connection and stop retries.
                        // Without this, the HTTP request continues running and holds a connection
                        // until the server responds — which is exactly the slow-server scenario
                        // that causes deadlines to fire.
                        delegateFuture.cancel(true);
                    }
                },
                remainingNanos,
                TimeUnit.NANOSECONDS);

        DialogueFutures.addDirectCallback(delegateFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Response result) {
                deadlineTask.cancel(false);
                if (result != null && !callerFuture.set(result)) {
                    result.close();
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                deadlineTask.cancel(false);
                callerFuture.setException(throwable);
            }
        });

        DialogueFutures.addDirectListener(callerFuture, () -> {
            if (callerFuture.isCancelled()) {
                delegateFuture.cancel(true);
            }
        });

        return callerFuture;
    }

    /**
     * Clamp the deadline duration to avoid {@link ArithmeticException} from
     * {@link Duration#toNanos()} on absurdly large values, and cap at a reasonable maximum.
     */
    private static long clampNanos(Duration duration) {
        try {
            return Math.min(duration.toNanos(), MAX_DEADLINE_NANOS);
        } catch (ArithmeticException _e) {
            return MAX_DEADLINE_NANOS;
        }
    }

    @Override
    public String toString() {
        return "DeadlineEnforcingChannel{" + delegate + '}';
    }
}
