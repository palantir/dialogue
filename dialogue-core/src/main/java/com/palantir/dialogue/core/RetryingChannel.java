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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immediately retries calls to the underlying channel upon failure.
 */
final class RetryingChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(RetryingChannel.class);

    private final LimitedChannel delegate;
    private final int maxRetries;
    private final ClientConfiguration.ServerQoS serverQoS;

    RetryingChannel(LimitedChannel delegate, int maxRetries, ClientConfiguration.ServerQoS serverQoS) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
        this.serverQoS = serverQoS;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return new RetryingCallback(delegate, endpoint, request, maxRetries, serverQoS).execute();
    }

    private static final class RetryingCallback {
        private final LimitedChannel delegate;
        private final Endpoint endpoint;
        private final Request request;
        private final int maxRetries;
        private final ClientConfiguration.ServerQoS serverQoS;
        private int failures = 0;

        private RetryingCallback(
                LimitedChannel delegate,
                Endpoint endpoint,
                Request request,
                int maxRetries,
                ClientConfiguration.ServerQoS serverQoS) {
            this.delegate = delegate;
            this.endpoint = endpoint;
            this.request = request;
            this.maxRetries = maxRetries;
            this.serverQoS = serverQoS;
        }

        ListenableFuture<Response> execute() {
            return wrap(delegate.maybeExecute(endpoint, request));
        }

        ListenableFuture<Response> success(Response response) {
            // this condition should really match the BlacklistingChannel so that we don't hit the same host twice in
            // a row
            if (Responses.isQosStatus(response)) {
                response.close();
                Throwable failure =
                        new SafeRuntimeException("Received retryable response", SafeArg.of("status", response.code()));
                if (++failures <= maxRetries) {
                    logRetry(failure);
                    return execute();
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

        ListenableFuture<Response> convertToResponse(LimitedResponse response) {
            response.matches(new LimitedResponse.Cases<ListenableFuture<Response>>() {
                @Override
                public ListenableFuture<Response> serverLimited(Response response) {
                    if (shouldPropagateQos(serverQoS)) {
                        return
                    }
                    return null;
                }

                @Override
                public ListenableFuture<Response> clientLimited() {
                    return null;
                }

                @Override
                public ListenableFuture<Response> success(Response response) {
                    return null;
                }
            });
        }

        ListenableFuture<Response> failure(Throwable throwable) {
            if (++failures <= maxRetries) {
                logRetry(throwable);
                return execute();
            }
            return Futures.immediateFailedFuture(throwable);
        }

        private void logRetry(Throwable throwable) {
            if (log.isInfoEnabled()) {
                log.info(
                        "Retrying call after failure",
                        SafeArg.of("failures", failures),
                        SafeArg.of("maxRetries", maxRetries),
                        SafeArg.of("serviceName", endpoint.serviceName()),
                        SafeArg.of("endpoint", endpoint.endpointName()),
                        throwable);
            }
        }

        private ListenableFuture<Response> wrap(ListenableFuture<LimitedResponse> input) {
            ListenableFuture<Response> result;
            if (!shouldPropagateQos(serverQoS)) {
                result = Futures.transformAsync(result, this::success, MoreExecutors.directExecutor());
            } else {
                result = Futures.transformAsync(result, )
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
