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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Immediately retries calls to the underlying channel upon failure.
 */
final class RetryingChannel implements Channel {
    private static final int DEFAULT_MAX_RETRIES = 4;
    private static final Executor DIRECT_EXECUTOR = MoreExecutors.directExecutor();

    private static final int UNAVAILABLE_503 = 503;
    private static final int TOO_MANY_REQUESTS_429 = 429;

    private final Channel delegate;
    private final int maxRetries;

    RetryingChannel(Channel delegate) {
        this(delegate, DEFAULT_MAX_RETRIES);
    }

    @VisibleForTesting
    RetryingChannel(Channel delegate, int maxRetries) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        SettableFuture<Response> future = SettableFuture.create();

        Function<Integer, ListenableFuture<Response>> callSupplier = attempt -> {
            // TODO(dfox): include retry number in the request somehow
            return delegate.execute(endpoint, request);
        };
        FutureCallback<Response> retryer = new RetryingCallback(callSupplier, future);
        Futures.addCallback(callSupplier.apply(0), retryer, DIRECT_EXECUTOR);

        return future;
    }

    private final class RetryingCallback implements FutureCallback<Response> {
        private final AtomicInteger failures = new AtomicInteger(0);
        private final Function<Integer, ListenableFuture<Response>> runnable;
        private final SettableFuture<Response> delegate;

        private RetryingCallback(
                Function<Integer, ListenableFuture<Response>> runnable, SettableFuture<Response> delegate) {
            this.runnable = runnable;
            this.delegate = delegate;
        }

        @Override
        public void onSuccess(Response response) {
            // this condition should really match the BlacklistingChannel so that we don't hit the same host twice in
            // a row
            if (response.code() == UNAVAILABLE_503 || response.code() == TOO_MANY_REQUESTS_429) {
                response.close();
                retryOrFail(Optional.empty());
                return;
            }

            // TODO(dfox): if people are using 308, we probably need to support it too

            boolean setSuccessfully = delegate.set(response);
            if (!setSuccessfully) {
                response.close();
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            retryOrFail(Optional.of(throwable));
        }

        private void retryOrFail(Optional<Throwable> throwable) {
            int attempt = failures.incrementAndGet();
            if (attempt < maxRetries) {
                Futures.addCallback(runnable.apply(attempt), this, DIRECT_EXECUTOR);
            } else {
                if (throwable.isPresent()) {
                    delegate.setException(throwable.get());
                } else {
                    delegate.setException(new SafeRuntimeException("Retries exhausted"));
                }
            }
        }
    }
}
