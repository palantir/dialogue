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
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immediately retries calls to the underlying channel upon failure.
 */
final class RetryingChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(RetryingChannel.class);

    private static final int UNAVAILABLE_503 = 503;
    private static final int TOO_MANY_REQUESTS_429 = 429;

    private final Channel delegate;
    private final int maxRetries;

    RetryingChannel(Channel delegate, int maxRetries) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return new RetryingCallback(delegate, endpoint, request, maxRetries).execute();
    }

    private static final class RetryingCallback {
        private final Channel delegate;
        private final Endpoint endpoint;
        private final Request request;
        private final int maxRetries;
        private int failures = 0;

        private RetryingCallback(Channel delegate, Endpoint endpoint, Request request, int maxRetries) {
            this.delegate = delegate;
            this.endpoint = endpoint;
            this.request = request;
            this.maxRetries = maxRetries;
        }

        ListenableFuture<Response> execute() {
            return wrap(delegate.execute(endpoint, request));
        }

        ListenableFuture<Response> success(Response response) {
            // this condition should really match the BlacklistingChannel so that we don't hit the same host twice in
            // a row
            if (response.code() == UNAVAILABLE_503 || response.code() == TOO_MANY_REQUESTS_429) {
                response.close();
                return failure(
                        new SafeRuntimeException("Received response status", SafeArg.of("status", response.code())));
            }

            // TODO(dfox): if people are using 308, we probably need to support it too

            return Futures.immediateFuture(response);
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

        private ListenableFuture<Response> wrap(ListenableFuture<Response> input) {
            return Futures.catchingAsync(
                    Futures.transformAsync(input, this::success, MoreExecutors.directExecutor()),
                    Throwable.class,
                    this::failure,
                    MoreExecutors.directExecutor());
        }
    }
}
