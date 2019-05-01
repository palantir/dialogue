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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class RetryingChannel implements Channel {
    private static final Executor direct = MoreExecutors.directExecutor();
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final Channel delegate;
    private final int maxRetries;

    RetryingChannel(Channel delegate) {
        this(delegate, DEFAULT_MAX_RETRIES);
    }

    RetryingChannel(Channel delegate, int maxRetries) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
    }

    @Override
    public ListenableFuture<Response> createCall(Endpoint endpoint, Request request) {
        SettableFuture<Response> future = SettableFuture.create();

        ListenableFuture<Response> call = delegate.createCall(endpoint, request);
        FutureCallback<Response> retryer = new RetryingCallback<>(() -> delegate.createCall(endpoint, request), future);
        Futures.addCallback(call, retryer, direct);

        return future;
    }

    private final class RetryingCallback<T> implements FutureCallback<T> {
        private final AtomicInteger failures = new AtomicInteger();
        private final Supplier<ListenableFuture<T>> runnable;
        private final SettableFuture<T> delegate;

        private RetryingCallback(Supplier<ListenableFuture<T>> runnable, SettableFuture<T> delegate) {
            this.runnable = runnable;
            this.delegate = delegate;
        }

        @Override
        public void onSuccess(T result) {
            delegate.set(result);
        }

        @Override
        public void onFailure(Throwable throwable) {
            if (failures.incrementAndGet() < maxRetries) {
                Futures.addCallback(runnable.get(), this, direct);
            } else {
                delegate.setException(throwable);
            }
        }
    }
}
