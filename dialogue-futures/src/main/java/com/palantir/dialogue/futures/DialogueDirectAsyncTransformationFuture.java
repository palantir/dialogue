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
 * {@link Futures#transformAsync(ListenableFuture, AsyncFunction, Executor)}
 * in two ways:
 * Firstly, it only allows transformations on the same thread. Second, calling cancel on this future
 * does not allow the input future to successfully set a result while this future reports cancellation.
 *
 * Note that this means it's possible for a cancel invocation to return false and fail to terminate the future,
 * which allows dialogue to close responses properly without leaking resources.
 */
final class DialogueDirectAsyncTransformationFuture<I, O> implements DialogueListenableFuture<O>, Runnable {
    private volatile ListenableFuture<?> currentFuture;
    private final ListenableFuture<O> output;

    @SuppressWarnings("unchecked")
    DialogueDirectAsyncTransformationFuture(ListenableFuture<I> input, AsyncFunction<? super I, ? extends O> function) {
        this.currentFuture = input;
        this.output = Futures.transformAsync(
                input,
                result -> {
                    ListenableFuture<O> future = (ListenableFuture<O>) function.apply(result);
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
        ListenableFuture<?> snapshot = currentFuture;
        return snapshot.isCancelled();
    }

    @Override
    public boolean isDone() {
        return output.isDone();
    }

    @Override
    public O get() throws InterruptedException, ExecutionException {
        return output.get();
    }

    @Override
    public O get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return output.get(timeout, unit);
    }

    /** Output completion listener. When the output future is completed, previous futures can be garbage collected. */
    @Override
    public void run() {
        this.currentFuture = output;
    }

    @Override
    public void failureCallback(Runnable onFailure) {

    }
}
