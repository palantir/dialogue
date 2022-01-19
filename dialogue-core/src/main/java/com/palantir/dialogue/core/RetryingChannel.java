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

import com.codahale.metrics.Meter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.TagTranslator;
import com.palantir.tracing.Tracers;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Retries failed requests by scheduling them onto a ScheduledExecutorService after an exponential backoff. */
final class RetryingChannel implements EndpointChannel {

    private static final SafeLogger log = SafeLoggerFactory.get(RetryingChannel.class);
    private static final String SCHEDULER_NAME = "dialogue-RetryingChannel-scheduler";

    /*
     * Shared single thread executor is reused between all retrying channels. If it becomes oversaturated
     * we may wait longer than expected before resuming requests, but this is an
     * edge case where services are already operating in a degraded state and we should not
     * spam servers.
     */
    @SuppressWarnings("deprecation") // Singleton registry for a singleton executor
    static final Supplier<ScheduledExecutorService> sharedScheduler =
            Suppliers.memoize(() -> DialogueExecutors.newSharedSingleThreadScheduler(MetricRegistries.instrument(
                    SharedTaggedMetricRegistries.getSingleton(),
                    new ThreadFactoryBuilder()
                            .setNameFormat(SCHEDULER_NAME + "-%d")
                            .setDaemon(true)
                            .build(),
                    SCHEDULER_NAME)));

    @SuppressWarnings("UnnecessaryLambda") // no allocations
    private static final BiFunction<Endpoint, Response, Throwable> qosThrowable = (_endpoint, response) ->
            new SafeRuntimeException("Received retryable response", SafeArg.of("status", response.code()));

    @SuppressWarnings("UnnecessaryLambda") // no allocations
    private static final BiFunction<Endpoint, Response, Throwable> serverErrorThrowable =
            (endpoint, response) -> new SafeRuntimeException(
                    "Received server error, but http method is safe to retry",
                    SafeArg.of("status", response.code()),
                    SafeArg.of("method", endpoint.httpMethod()));

    private final ListeningScheduledExecutorService scheduler;
    private final EndpointChannel delegate;
    private final Endpoint endpoint;
    private final String channelName;
    private final int maxRetries;
    private final ClientConfiguration.ServerQoS serverQoS;
    private final ClientConfiguration.RetryOnTimeout retryOnTimeout;
    private final Duration backoffSlotSize;
    private final DoubleSupplier jitter;
    private final Meter retryDueToServerError;
    private final Meter retryDueToQosResponse;
    private final Function<Throwable, Meter> retryDueToThrowable;

    static EndpointChannel create(Config cf, EndpointChannel channel, Endpoint endpoint) {
        ClientConfiguration clientConf = cf.clientConf();
        if (clientConf.maxNumRetries() == 0) {
            return channel;
        }

        if (cf.mesh() == MeshMode.USE_EXTERNAL_MESH) {
            log.debug(
                    "Disabling retrying channel due to MeshMode",
                    SafeArg.of("channel", cf.channelName()),
                    SafeArg.of("ignoredMaxNumRetries", clientConf.maxNumRetries()));
            return channel;
        }

        return new RetryingChannel(
                channel,
                endpoint,
                cf.channelName(),
                clientConf.taggedMetricRegistry(),
                clientConf.maxNumRetries(),
                clientConf.backoffSlotSize(),
                clientConf.serverQoS(),
                clientConf.retryOnTimeout(),
                cf.scheduler(),
                cf.random()::nextDouble);
    }

    @VisibleForTesting
    RetryingChannel(
            EndpointChannel delegate,
            Endpoint endpoint,
            String channelName,
            int maxRetries,
            Duration backoffSlotSize,
            ClientConfiguration.ServerQoS serverQoS,
            ClientConfiguration.RetryOnTimeout retryOnTimeout) {
        this(
                delegate,
                endpoint,
                channelName,
                new DefaultTaggedMetricRegistry(),
                maxRetries,
                backoffSlotSize,
                serverQoS,
                retryOnTimeout,
                sharedScheduler.get(),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    private RetryingChannel(
            EndpointChannel delegate,
            Endpoint endpoint,
            String channelName,
            TaggedMetricRegistry metrics,
            int maxRetries,
            Duration backoffSlotSize,
            ClientConfiguration.ServerQoS serverQoS,
            ClientConfiguration.RetryOnTimeout retryOnTimeout,
            ScheduledExecutorService scheduler,
            DoubleSupplier jitter) {
        this.delegate = delegate;
        this.endpoint = endpoint;
        this.channelName = channelName;
        this.maxRetries = maxRetries;
        this.backoffSlotSize = backoffSlotSize;
        this.serverQoS = serverQoS;
        this.retryOnTimeout = retryOnTimeout;
        this.scheduler = instrument(scheduler, metrics);
        this.jitter = jitter;
        DialogueClientMetrics dialogueClientMetrics = DialogueClientMetrics.of(metrics);
        this.retryDueToServerError = dialogueClientMetrics
                .requestRetry()
                .channelName(channelName)
                .reason("serverError")
                .build();
        this.retryDueToQosResponse = dialogueClientMetrics
                .requestRetry()
                .channelName(channelName)
                .reason("qosResponse")
                .build();
        this.retryDueToThrowable = throwable -> dialogueClientMetrics
                .requestRetry()
                .channelName(channelName)
                .reason(throwable.getClass().getSimpleName())
                .build();
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        if (isRetryable(request)) {
            Optional<SafeRuntimeException> debugStacktrace = log.isDebugEnabled()
                    ? Optional.of(new SafeRuntimeException("Exception for stacktrace"))
                    : Optional.empty();
            return new RetryingCallback(endpoint, request, debugStacktrace).execute();
        }
        return delegate.execute(request);
    }

    private static boolean isRetryable(Request request) {
        Optional<RequestBody> maybeBody = request.body();
        return !maybeBody.isPresent() || maybeBody.get().repeatable();
    }

    @Override
    public String toString() {
        return "RetryingChannel{maxRetries=" + maxRetries + ", serverQoS=" + serverQoS + " delegate=" + delegate + '}';
    }

    private enum RetryingCallbackTranslator implements TagTranslator<RetryingCallback> {
        INSTANCE;

        @Override
        public <T> void translate(TagAdapter<T> sink, T target, RetryingCallback data) {
            sink.tag(target, "serviceName", data.endpoint.serviceName());
            sink.tag(target, "endpointName", data.endpoint.endpointName());
            sink.tag(target, "failures", Integer.toString(data.failures));
            sink.tag(target, "channel", data.channelName());
        }
    }

    private final class RetryingCallback {
        private final Endpoint endpoint;
        private final Request request;
        private final Optional<SafeRuntimeException> callsiteStacktrace;
        private final DetachedSpan span = DetachedSpan.start("Dialogue-RetryingChannel");
        private int failures = 0;

        private RetryingCallback(
                Endpoint endpoint, Request request, Optional<SafeRuntimeException> callsiteStacktrace) {
            this.endpoint = endpoint;
            this.request = request;
            this.callsiteStacktrace = callsiteStacktrace;
        }

        ListenableFuture<Response> execute() {
            ListenableFuture<Response> result = wrap(delegate.execute(request));
            result.addListener(
                    () -> {
                        if (failures > 0) {
                            span.complete(RetryingCallbackTranslator.INSTANCE, this);
                        }
                    },
                    DialogueFutures.safeDirectExecutor());
            return result;
        }

        private ListenableFuture<Response> wrap(ListenableFuture<Response> input) {
            ListenableFuture<Response> result = input;
            result = DialogueFutures.transformAsync(result, this::handleHttpResponse);
            result = DialogueFutures.catchingAllAsync(result, this::handleThrowable);
            return result;
        }

        private ListenableFuture<Response> handleHttpResponse(Response response) {
            if (isRetryableQosStatus(response)) {
                return incrementFailuresAndMaybeRetry(response, qosThrowable, retryDueToQosResponse);
            }

            if (Responses.isInternalServerError(response) && safeToRetry(endpoint.httpMethod())) {
                return incrementFailuresAndMaybeRetry(response, serverErrorThrowable, retryDueToServerError);
            }

            return Futures.immediateFuture(response);
        }

        private ListenableFuture<Response> handleThrowable(Throwable clientSideThrowable) {
            if (++failures <= maxRetries) {
                if (shouldAttemptToRetry(clientSideThrowable)) {
                    callsiteStacktrace.ifPresent(clientSideThrowable::addSuppressed);
                    Meter retryReason = retryDueToThrowable.apply(clientSideThrowable);
                    long backoffNanoseconds = getBackoffNanoseconds();
                    infoLogRetry(backoffNanoseconds, OptionalInt.empty(), clientSideThrowable);
                    return scheduleRetry(retryReason, backoffNanoseconds);
                } else if (log.isDebugEnabled()) {
                    callsiteStacktrace.ifPresent(clientSideThrowable::addSuppressed);
                    log.debug(
                            "Not attempting to retry failure. channel: {}, service: {}, endpoint: {}",
                            SafeArg.of("channelName", channelName),
                            SafeArg.of("serviceName", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName()),
                            clientSideThrowable);
                }
            }
            return Futures.immediateFailedFuture(clientSideThrowable);
        }

        private ListenableFuture<Response> incrementFailuresAndMaybeRetry(
                Response response, BiFunction<Endpoint, Response, Throwable> failureSupplier, Meter meter) {
            if (++failures <= maxRetries) {
                response.close();
                Throwable throwableToLog = log.isTraceEnabled() ? failureSupplier.apply(endpoint, response) : null;
                long backoffNanos = Responses.isRetryOther(response) ? 0 : getBackoffNanoseconds();
                infoLogRetry(backoffNanos, OptionalInt.of(response.code()), throwableToLog);
                return scheduleRetry(meter, backoffNanos);
            }
            infoLogRetriesExhausted(response);
            // not closing response because ConjureBodySerde will need to deserialize it
            return Futures.immediateFuture(response);
        }

        @SuppressWarnings({"FutureReturnValueIgnored", "CheckReturnValue"})
        private ListenableFuture<Response> scheduleRetry(Meter meter, long backoffNanoseconds) {
            meter.mark();
            if (backoffNanoseconds <= 0) {
                return wrap(delegate.execute(request));
            }
            DetachedSpan backoffSpan = span.childDetachedSpan("retry-backoff");
            // Code beyond this line implements the following without risking leaking responses:
            // ListenableScheduledFuture<ListenableFuture<Response>> scheduled = scheduler.schedule(
            //     () -> {
            //         backoffSpan.complete(RetryingCallbackTranslator.INSTANCE, this);
            //         return delegate.execute(request);
            //     }, backoffNanoseconds, TimeUnit.NANOSECONDS);
            // return wrap(DialogueFutures.transformAsync(scheduled, input -> input));
            SettableFuture<Response> responseFuture = SettableFuture.create();
            // Scheduler future must not be cancelled, otherwise responses will leak.
            scheduler.schedule(
                    () -> {
                        backoffSpan.complete(RetryingCallbackTranslator.INSTANCE, this);
                        if (responseFuture.isDone()) {
                            return;
                        }
                        ListenableFuture<Response> delegateResult = delegate.execute(request);
                        DialogueFutures.addDirectCallback(delegateResult, new FutureCallback<>() {
                            @Override
                            public void onSuccess(Response result) {
                                if (!responseFuture.set(result)) {
                                    result.close();
                                }
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                if (delegateResult.isCancelled()) {
                                    responseFuture.cancel(false);
                                } else if (!responseFuture.setException(throwable)) {
                                    log.info("Response future completed before delegate threw", throwable);
                                }
                            }
                        });
                        DialogueFutures.addDirectListener(responseFuture, () -> {
                            if (responseFuture.isCancelled()) {
                                delegateResult.cancel(false);
                            }
                        });
                    },
                    backoffNanoseconds,
                    TimeUnit.NANOSECONDS);
            return wrap(responseFuture);
        }

        private long getBackoffNanoseconds() {
            if (failures == 0) {
                return 0L;
            }
            int upperBound = (int) Math.pow(2, failures - 1);
            return Math.round(backoffSlotSize.toNanos() * jitter.getAsDouble() * upperBound);
        }

        private boolean isRetryableQosStatus(Response response) {
            switch (serverQoS) {
                case AUTOMATIC_RETRY:
                    return Responses.isQosStatus(response);
                case PROPAGATE_429_and_503_TO_CALLER:
                    return Responses.isQosStatus(response)
                            && !Responses.isTooManyRequests(response)
                            && !Responses.isUnavailable(response);
            }
            throw new SafeIllegalStateException(
                    "Encountered unknown propagate QoS configuration", SafeArg.of("serverQoS", serverQoS));
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
            // Only retry IOExceptions. Other failures, particularly RuntimeException and Error are not
            // meant to be recovered from.
            return throwable instanceof IOException;
        }

        private void infoLogRetriesExhausted(Response response) {
            if (log.isInfoEnabled()) {
                SafeRuntimeException stacktrace = callsiteStacktrace.orElse(null);
                log.info(
                        "Exhausted {} retries, returning last received response with "
                                + "status {}, channel: {}, service: {}, endpoint: {}",
                        SafeArg.of("retries", maxRetries),
                        SafeArg.of("status", response.code()),
                        SafeArg.of("channelName", channelName),
                        SafeArg.of("serviceName", endpoint.serviceName()),
                        SafeArg.of("endpoint", endpoint.endpointName()),
                        stacktrace);
            }
        }

        private void infoLogRetry(long backoffNanoseconds, OptionalInt responseStatus, @Nullable Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info(
                        "Retrying call after failure {}/{} backoff: {}, channel: {}, service: {}, endpoint: {}, "
                                + "status: {}",
                        SafeArg.of("failures", failures),
                        SafeArg.of("maxRetries", maxRetries),
                        SafeArg.of("backoffMillis", TimeUnit.NANOSECONDS.toMillis(backoffNanoseconds)),
                        SafeArg.of("channelName", channelName),
                        SafeArg.of("serviceName", endpoint.serviceName()),
                        SafeArg.of("endpoint", endpoint.endpointName()),
                        SafeArg.of("status", responseStatus.isPresent() ? responseStatus.getAsInt() : null),
                        throwable);
            }
        }

        private String channelName() {
            return channelName;
        }
    }

    /**
     * We are a bit more conservative than the definition of Safe and Idempotent in https://tools.ietf
     * .org/html/rfc7231#section-4.2.1, as we're not sure whether developers have written non-idempotent PUT/DELETE
     * endpoints.
     */
    private static boolean safeToRetry(HttpMethod httpMethod) {
        switch (httpMethod) {
            case GET:
            case HEAD:
            case OPTIONS:
            case PUT:
            case DELETE:
                return true;
            case POST:
            case PATCH:
                return false;
        }

        throw new SafeIllegalStateException("Unknown method", SafeArg.of("httpMethod", httpMethod));
    }

    private static ListeningScheduledExecutorService instrument(
            ScheduledExecutorService delegate, TaggedMetricRegistry metrics) {
        return MoreExecutors.listeningDecorator(
                Tracers.wrap(SCHEDULER_NAME, MetricRegistries.instrument(metrics, delegate, SCHEDULER_NAME)));
    }
}
