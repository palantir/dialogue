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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.Tracers;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immediately retries calls to the underlying channel upon failure.
 */
final class RetryingChannel implements Channel {

    static final int NUM_SCHEDULING_THREADS = 5;
    private static final Logger log = LoggerFactory.getLogger(RetryingChannel.class);
    private static final Supplier<ListeningScheduledExecutorService> schedulingExecutor = () ->
            MoreExecutors.listeningDecorator(
                    Tracers.wrap(Executors.newScheduledThreadPool(NUM_SCHEDULING_THREADS, new ThreadFactoryBuilder()
                            .setNameFormat("dialogue-retrying-channel-%d")
                            .setDaemon(true)
                            .build())));

    private final LimitedChannel delegate;
    private final ClientConfiguration.ServerQoS serverQoS;
    private final BackoffStrategy backoffStrategy;
    private final ListeningScheduledExecutorService scheduledExecutorService;

    RetryingChannel(LimitedChannel delegate, ClientConfiguration.ServerQoS serverQoS, BackoffStrategy backoffStrategy) {
        this(delegate, serverQoS, backoffStrategy, schedulingExecutor.get());
    }

    @VisibleForTesting
    RetryingChannel(
            LimitedChannel delegate,
            ClientConfiguration.ServerQoS serverQoS,
            BackoffStrategy backoffStrategy,
            ListeningScheduledExecutorService scheduledExecutorService) {
        this.delegate = delegate;
        this.serverQoS = serverQoS;
        this.backoffStrategy = backoffStrategy;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return new RetryingCallback(delegate, endpoint, request, serverQoS, backoffStrategy, scheduledExecutorService)
                .execute();
    }

    private static final class RetryingCallback {
        private final LimitedChannel delegate;
        private final Endpoint endpoint;
        private final Request request;
        private final ClientConfiguration.ServerQoS serverQoS;
        private final BackoffStrategy backoffStrategy;
        private final ListeningScheduledExecutorService scheduledExecutorService;

        private RetryingCallback(
                LimitedChannel delegate,
                Endpoint endpoint,
                Request request,
                ClientConfiguration.ServerQoS serverQoS,
                BackoffStrategy backoffStrategy,
                ListeningScheduledExecutorService scheduledExecutorService) {
            this.delegate = delegate;
            this.endpoint = endpoint;
            this.request = request;
            this.serverQoS = serverQoS;
            this.backoffStrategy = backoffStrategy;
            this.scheduledExecutorService = scheduledExecutorService;
        }

        ListenableFuture<Response> execute() {
            return wrap(delegate.maybeExecute(endpoint, request));
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        private ListenableFuture<Response> scheduleExecution(Duration backoff) {
            // TODO(rfink): Investigate whether ignoring the ScheduledFuture is safe, #629.
            ListenableScheduledFuture<?> schedule =
                    scheduledExecutorService.schedule(() -> {}, backoff.toMillis(), TimeUnit.MILLISECONDS);
            return Futures.transformAsync(schedule, input -> execute(), MoreExecutors.directExecutor());
        }

        ListenableFuture<Response> convertToResponse(LimitedResponse response) {
            return response.matches(new LimitedResponse.Cases<ListenableFuture<Response>>() {
                @Override
                public ListenableFuture<Response> serverLimited(Response response) {
                    if (shouldPropagateQos(serverQoS)) {
                        return Futures.immediateFuture(response);
                    }
                    response.close();
                    Throwable failure = new SafeRuntimeException(
                            "Received retryable response", SafeArg.of("status", response.code()));
                    Optional<Duration> backOff = backoffStrategy.nextBackoff();
                    if (backOff.isPresent()) {
                        logRetry(failure);
                        return scheduleExecution(backOff.get());
                    }
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Retries exhausted, returning a retryable response with status {}",
                                SafeArg.of("status", response.code()));
                    }
                    return Futures.immediateFuture(response);
                }

                @Override
                public ListenableFuture<Response> clientLimited() {
                    // Always retry client side limited requests
                    return execute();
                }

                @Override
                public ListenableFuture<Response> clientBackOff() {
                    Optional<Duration> backOff = backoffStrategy.nextBackoff();
                    if (backOff.isPresent()) {
                        logRetry(failure);
                        return scheduleExecution(backOff.get());
                    }
                    return null;
                }

                @Override
                public ListenableFuture<Response> serverError(Response response) {
                    return Futures.immediateFuture(response);
                }

                @Override
                public ListenableFuture<Response> success(Response response) {
                    return Futures.immediateFuture(response);
                }
            });
        }

        ListenableFuture<Response> failure(Throwable throwable) {
            Optional<Duration> backOff = backoffStrategy.nextBackoff();
            if (backOff.isPresent()) {
                logRetry(throwable);
                return scheduleExecution(backOff.get());
            }
            return Futures.immediateFailedFuture(throwable);
        }

        private void logRetry(Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info(
                        "Retrying call after failure",
                        SafeArg.of("backOffStrategy", backoffStrategy),
                        SafeArg.of("serviceName", endpoint.serviceName()),
                        SafeArg.of("endpoint", endpoint.endpointName()),
                        throwable);
            }
        }

        private ListenableFuture<Response> wrap(ListenableFuture<LimitedResponse> input) {
            ListenableFuture<Response> result =
                    Futures.transformAsync(input, this::convertToResponse, MoreExecutors.directExecutor());
            result = Futures.catchingAsync(result, Throwable.class, this::failure, MoreExecutors.directExecutor());
            return result;
        }
    }

    private static boolean shouldPropagateQos(ClientConfiguration.ServerQoS serverQoS) {
        switch (serverQoS) {
            case PROPAGATE_429_and_503_TO_CALLER:
                return true;
            case AUTOMATIC_RETRY:
                return false;
        }

        throw new SafeIllegalStateException(
                "Encountered unknown propagate QoS configuration", SafeArg.of("serverQoS", serverQoS));
    }

    /** Defines a strategy for waiting in between successive retries of an operation that is subject to failure. */
    interface BackoffStrategy {
        /**
         * Returns the next suggested backoff duration, or {@link Optional#empty} if the operation should not be retried
         * again.
         */
        Optional<Duration> nextBackoff();
    }

    /**
     * Implements "exponential backoff with full jitter", suggesting a backoff duration chosen randomly from the interval
     * {@code [0, backoffSlotSize * 2^c)} for the c-th retry for a maximum of {@link #maxNumRetries} retries.
     */
    static final class ExponentialBackoff implements BackoffStrategy {

        private final int maxNumRetries;
        private final Duration backoffSlotSize;
        private final DoubleSupplier random;

        private int retryNumber = 0;

        ExponentialBackoff(int maxNumRetries, Duration backoffSlotSize) {
            this(maxNumRetries, backoffSlotSize, ExponentialBackoff::random);
        }

        @VisibleForTesting
        ExponentialBackoff(int maxNumRetries, Duration backoffSlotSize, DoubleSupplier random) {
            this.maxNumRetries = maxNumRetries;
            this.backoffSlotSize = backoffSlotSize;
            this.random = random;
        }

        @Override
        public Optional<Duration> nextBackoff() {
            retryNumber += 1;
            if (retryNumber > maxNumRetries) {
                return Optional.empty();
            }

            int upperBound = (int) Math.pow(2, retryNumber);
            return Optional.of(
                    Duration.ofNanos(Math.round(backoffSlotSize.toNanos() * random.getAsDouble() * upperBound)));
        }

        private static double random() {
            return ThreadLocalRandom.current().nextDouble();
        }

        @Override
        public String toString() {
            return "ExponentialBackoff{maxNumRetries="
                    + maxNumRetries
                    + ", backoffSlotSize="
                    + backoffSlotSize
                    + ", retryNumber="
                    + retryNumber
                    + '}';
        }
    }
}
