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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * This {@link ListenableFuture} implementation differs from
 * {@link Futures#transform(ListenableFuture, com.google.common.base.Function, Executor)}
 * in two ways:
 * Firstly, it only allows transformations on the same thread. Second, calling cancel on this future
 * does not allow the input future to successfully set a result while this future reports cancellation.
 * Note that this means it's possible for a cancel invocation to return false and fail to terminate the future,
 * which allows dialogue to close responses properly without leaking resources.
 */
final class DialogueDirectTransformationFuture<I, O> implements ListenableFuture<O>, FutureCallback<I> {
    private final ListenableFuture<I> input;
    private final SettableFuture<O> output;
    private final Function<? super I, ? extends O> function;

    DialogueDirectTransformationFuture(ListenableFuture<I> input, Function<? super I, ? extends O> function) {
        this.input = input;
        this.function = function;
        this.output = SettableFuture.create();
        Futures.addCallback(input, this, MoreExecutors.directExecutor());
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        output.addListener(listener, executor);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // Cancel apples to the input future which will update the output future immediately.
        return input.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        // isCancelled reflects the state of the original input.
        return input.isCancelled();
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

    // Future callback methods update the output future based on the input.

    @Override
    public void onSuccess(I result) {
        try {
            output.set(function.apply(result));
        } catch (Throwable t) {
            output.setException(t);
        }
    }

    @Override
    public void onFailure(Throwable throwable) {
        if (input.isCancelled()) {
            output.cancel(false);
        } else {
            output.setException(throwable);
        }
    }
}
