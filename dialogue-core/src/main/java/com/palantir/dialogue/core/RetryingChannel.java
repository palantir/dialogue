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
import com.google.common.base.Suppliers;
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
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.Tracers;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    private static final Logger log = LoggerFactory.getLogger(RetryingChannel.class);
    private static final String SCHEDULER_NAME = "dialogue-RetryingChannel-scheduler";

    /*
     * Shared single thread executor is reused between all retrying channels. If it becomes oversaturated
     * we may wait longer than expected before resuming requests, but this is an
     * edge case where services are already operating in a degraded state and we should not
     * spam servers.
     */
    static final Supplier<ScheduledExecutorService> sharedScheduler =
            Suppliers.memoize(() -> Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                    .setNameFormat(SCHEDULER_NAME + "-%d")
                    .setDaemon(false)
                    .build()));

    private final ListeningScheduledExecutorService scheduler;
    private final Channel delegate;
    private final String serviceName;
    private final int maxRetries;
    private final ClientConfiguration.ServerQoS serverQoS;
    private final ClientConfiguration.RetryOnTimeout retryOnTimeout;
    private final Duration backoffSlotSize;
    private final DoubleSupplier jitter;

    @VisibleForTesting
    RetryingChannel(
            Channel delegate,
            String serviceName,
            int maxRetries,
            Duration backoffSlotSize,
            ClientConfiguration.ServerQoS serverQoS,
            ClientConfiguration.RetryOnTimeout retryOnTimeout) {
        this(
                delegate,
                serviceName,
                new DefaultTaggedMetricRegistry(),
                maxRetries,
                backoffSlotSize,
                serverQoS,
                retryOnTimeout,
                sharedScheduler.get(),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    RetryingChannel(
            Channel delegate,
            String serviceName,
            TaggedMetricRegistry metrics,
            int maxRetries,
            Duration backoffSlotSize,
            ClientConfiguration.ServerQoS serverQoS,
            ClientConfiguration.RetryOnTimeout retryOnTimeout,
            ScheduledExecutorService scheduler,
            DoubleSupplier jitter) {
        this.delegate = delegate;
        this.serviceName = serviceName;
        this.maxRetries = maxRetries;
        this.backoffSlotSize = backoffSlotSize;
        this.serverQoS = serverQoS;
        this.retryOnTimeout = retryOnTimeout;
        this.scheduler = instrument(scheduler, metrics);
        this.jitter = jitter;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return new RetryingCallback(endpoint, request).execute();
    }

    private final class RetryingCallback {
        private final Endpoint endpoint;
        private final Request request;
        private final DetachedSpan span = DetachedSpan.start("Dialogue-RetryingChannel");
        private int failures = 0;

        private RetryingCallback(Endpoint endpoint, Request request) {
            this.endpoint = endpoint;
            this.request = request;
        }

        ListenableFuture<Response> execute() {
            ListenableFuture<Response> result = wrap(delegate.execute(endpoint, request));
            result.addListener(
                    () -> {
                        if (failures > 0) {
                            span.complete();
                        }
                    },
                    MoreExecutors.directExecutor());
            return result;
        }

        @SuppressWarnings("FutureReturnValueIgnored") // error-prone bug
        ListenableFuture<Response> retry(Throwable cause) {
            long backoffNanoseconds = getBackoffNanoseconds();
            logRetry(backoffNanoseconds, cause);
            if (backoffNanoseconds <= 0) {
                return wrap(delegate.execute(endpoint, request));
            }
            DetachedSpan backoffSpan = span.childDetachedSpan("retry-backoff-" + failures);
            ListenableScheduledFuture<ListenableFuture<Response>> scheduled = scheduler.schedule(
                    () -> {
                        backoffSpan.complete();
                        return delegate.execute(endpoint, request);
                    },
                    backoffNanoseconds,
                    TimeUnit.NANOSECONDS);
            return wrap(Futures.transformAsync(scheduled, input -> input, MoreExecutors.directExecutor()));
        }

        private long getBackoffNanoseconds() {
            if (failures == 0) {
                return 0L;
            }
            int upperBound = (int) Math.pow(2, failures - 1);
            return Math.round(backoffSlotSize.toNanos() * jitter.getAsDouble() * upperBound);
        }

        ListenableFuture<Response> success(Response response) {
            // this condition should really match the BlacklistingChannel so that we don't hit the same host twice in
            // a row
            if (Responses.isQosStatus(response)) {
                response.close();
                Throwable failure =
                        new SafeRuntimeException("Received retryable response", SafeArg.of("status", response.code()));
                if (++failures <= maxRetries) {
                    return retry(failure);
                }
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Exhausted {} retries, returning a retryable response with status {}",
                            SafeArg.of("retries", maxRetries),
                            SafeArg.of("status", response.code()));
                }
                return Futures.immediateFuture(response);
            }

            // TODO(dfox): if people are using 308, we probably need to support it too

            return Futures.immediateFuture(response);
        }

        ListenableFuture<Response> failure(Throwable throwable) {
            if (++failures <= maxRetries) {
                if (shouldAttemptToRetry(throwable)) {
                    return retry(throwable);
                } else if (log.isDebugEnabled()) {
                    log.debug(
                            "Not attempting to retry failure",
                            SafeArg.of("serviceName", serviceName),
                            SafeArg.of("endpoint", endpoint.endpointName()),
                            throwable);
                }
            }
            return Futures.immediateFailedFuture(throwable);
        }

        private boolean shouldAttemptToRetry(Throwable throwable) {
            if (retryOnTimeout == ClientConfiguration.RetryOnTimeout.DISABLED) {
                if (throwable instanceof SocketTimeoutException) {
                    // non-connect timeouts should not be retried
                    SocketTimeoutException socketTimeout = (SocketTimeoutException) throwable;
                    return socketTimeout.getMessage() != null
                            // String matches CJR RemotingOkHttpCall.shouldRetry
                            && socketTimeout.getMessage().contains("connect timed out");
                }
            }
            return true;
        }

        private void logRetry(long backoffNanoseconds, Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info(
                        "Retrying call after failure",
                        SafeArg.of("failures", failures),
                        SafeArg.of("maxRetries", maxRetries),
                        SafeArg.of("backoffMillis", TimeUnit.NANOSECONDS.toMillis(backoffNanoseconds)),
                        SafeArg.of("serviceName", serviceName),
                        SafeArg.of("endpoint", endpoint.endpointName()),
                        throwable);
            }
        }

        private ListenableFuture<Response> wrap(ListenableFuture<Response> input) {
            ListenableFuture<Response> result = input;
            if (!shouldPropagateQos(serverQoS)) {
                result = Futures.transformAsync(result, this::success, MoreExecutors.directExecutor());
            }
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

    private static ListeningScheduledExecutorService instrument(
            ScheduledExecutorService delegate, TaggedMetricRegistry metrics) {
        return MoreExecutors.listeningDecorator(
                Tracers.wrap(SCHEDULER_NAME, MetricRegistries.instrument(metrics, delegate, SCHEDULER_NAME)));
    }
}
