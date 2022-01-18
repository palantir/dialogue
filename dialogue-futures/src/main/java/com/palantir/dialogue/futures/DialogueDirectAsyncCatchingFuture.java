/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.futures;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This {@link ListenableFuture} implementation differs from
 * {@link Futures#catchingAsync(ListenableFuture, Class, AsyncFunction, Executor)}
 * in three ways:
 * Firstly, it only allows transformations on the same thread. Second, calling cancel on this future
 * does not allow the input future to successfully set a result while this future reports cancellation.
 * Third, it catches all exceptions rather than a subset based on a type check.
 *
 * Note that this means it's possible for a cancel invocation to return false and fail to terminate the future,
 * which allows dialogue to close responses properly without leaking resources.
 */
final class DialogueDirectAsyncCatchingFuture<T> implements ListenableFuture<T>, Runnable {
    private volatile ListenableFuture<T> currentFuture;
    private final ListenableFuture<T> output;

    DialogueDirectAsyncCatchingFuture(ListenableFuture<T> input, AsyncFunction<? super Throwable, T> function) {
        this.currentFuture = input;
        this.output = Futures.catchingAsync(
                input,
                Throwable.class,
                throwable -> {
                    // throwable may be a CancellationException
                    if (input.isCancelled()) {
                        return Futures.immediateCancelledFuture();
                    }
                    ListenableFuture<T> future = function.apply(throwable);
                    currentFuture = future;
                    return future;
                },
                DialogueFutures.safeDirectExecutor());
        output.addListener(this, DialogueFutures.safeDirectExecutor());
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        output.addListener(listener, executor);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        ListenableFuture<?> snapshot = currentFuture;
        return snapshot.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return output.isCancelled();
    }

    @Override
    public boolean isDone() {
        return output.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return output.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return output.get(timeout, unit);
    }

    /** Output completion listener. When the output future is completed, previous futures can be garbage collected. */
    @Override
    public void run() {
        this.currentFuture = output;
    }
}
