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
import com.palantir.tracing.Tracers;
import java.time.Duration;
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

    private static final Logger log = LoggerFactory.getLogger(RetryingChannel.class);

    /*
     * Shared single thread executor is reused between all retrying channels. If it becomes oversaturated
     * we may wait longer than expected before resuming requests, but this is an
     * edge case where services are already operating in a degraded state and we should not
     * spam servers.
     */
    private static final Supplier<ListeningScheduledExecutorService> sharedScheduler = Suppliers.memoize(
            () -> MoreExecutors.listeningDecorator(Tracers.wrap(
                    "dialogue-BlacklistingChannel-scheduler",
                    Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                            .setNameFormat("dialogue-BlacklistingChannel-scheduler-%d")
                            .setDaemon(false)
                            .build()))));

    private final ListeningScheduledExecutorService scheduler;
    private final Channel delegate;
    private final int maxRetries;
    private final ClientConfiguration.ServerQoS serverQoS;
    private final Duration backoffSlotSize;
    private final DoubleSupplier random;

    RetryingChannel(
            Channel delegate, int maxRetries, Duration backoffSlotSize, ClientConfiguration.ServerQoS serverQoS) {
        this(delegate, maxRetries, backoffSlotSize, serverQoS, sharedScheduler.get(), () ->
                ThreadLocalRandom.current().nextDouble());
    }

    RetryingChannel(
            Channel delegate,
            int maxRetries,
            Duration backoffSlotSize,
            ClientConfiguration.ServerQoS serverQoS,
            ListeningScheduledExecutorService scheduler,
            DoubleSupplier random) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
        this.backoffSlotSize = backoffSlotSize;
        this.serverQoS = serverQoS;
        this.scheduler = scheduler;
        this.random = random;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return new RetryingCallback(endpoint, request).execute();
    }

    private final class RetryingCallback {
        private final Endpoint endpoint;
        private final Request request;
        private int failures = 0;

        private RetryingCallback(Endpoint endpoint, Request request) {
            this.endpoint = endpoint;
            this.request = request;
        }

        ListenableFuture<Response> execute() {
            return wrap(delegate.execute(endpoint, request));
        }

        @SuppressWarnings("FutureReturnValueIgnored") // error-prone bug
        ListenableFuture<Response> retry(Throwable cause) {
            long backoffNanoseconds = getBackoffNanoseconds();
            logRetry(backoffNanoseconds, cause);
            if (backoffNanoseconds <= 0) {
                return wrap(delegate.execute(endpoint, request));
            }
            ListenableScheduledFuture<ListenableFuture<Response>> scheduled = scheduler.schedule(
                    () -> delegate.execute(endpoint, request), backoffNanoseconds, TimeUnit.NANOSECONDS);
            return wrap(Futures.transformAsync(scheduled, input -> input, MoreExecutors.directExecutor()));
        }

        private long getBackoffNanoseconds() {
            if (failures == 0) {
                return 0L;
            }
            int upperBound = (int) Math.pow(2, failures - 1);
            return Math.round(backoffSlotSize.toNanos() * random.getAsDouble() * upperBound);
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
                            "Retries exhausted, returning a retryable response with status {}",
                            SafeArg.of("status", response.code()));
                }
                return Futures.immediateFuture(response);
            }

            // TODO(dfox): if people are using 308, we probably need to support it too

            return Futures.immediateFuture(response);
        }

        ListenableFuture<Response> failure(Throwable throwable) {
            if (++failures <= maxRetries) {
                return retry(throwable);
            }
            return Futures.immediateFailedFuture(throwable);
        }

        private void logRetry(long backoffNanoseconds, Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info(
                        "Retrying call after failure",
                        SafeArg.of("failures", failures),
                        SafeArg.of("maxRetries", maxRetries),
                        SafeArg.of("backoffNanoseconds", backoffNanoseconds),
                        SafeArg.of("serviceName", endpoint.serviceName()),
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
}
