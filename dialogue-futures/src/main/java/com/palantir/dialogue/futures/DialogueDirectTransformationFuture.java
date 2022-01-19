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
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.logsafe.Preconditions;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This {@link ListenableFuture} implementation differs from
 * {@link Futures#transform(ListenableFuture, com.google.common.base.Function, Executor)}
 * in two ways:
 * Firstly, it only allows transformations on the same thread. Second, calling cancel on this future
 * does not allow the input future to successfully set a result while this future reports cancellation.
 * Note that this means it's possible for a cancel invocation to return false and fail to terminate the future,
 * which allows dialogue to close responses properly without leaking resources.
 */
final class DialogueDirectTransformationFuture<I, O> implements DialogueListenableFuture<O>, FutureCallback<I> {
    /** Freed upon completion to allow inputs to be garbage collected. */
    @Nullable
    private DialogueListenableFuture<I> input;

    @Nullable
    private Function<? super I, ? extends O> function;

    private final SettableFuture<O> output;

    DialogueDirectTransformationFuture(DialogueListenableFuture<I> input, Function<? super I, ? extends O> function) {
        this.input = input;
        this.function = function;
        this.output = SettableFuture.create();
        Futures.addCallback(input, this, DialogueFutures.safeDirectExecutor());
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        output.addListener(listener, executor);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        ListenableFuture<I> inputSnapshot = input;
        if (inputSnapshot != null) {
            // Cancel apples to the input future which will update the output future immediately.
            return inputSnapshot.cancel(mayInterruptIfRunning);
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        ListenableFuture<I> inputSnapshot = input;
        if (inputSnapshot != null) {
            // isCancelled reflects the state of the original input.
            return inputSnapshot.isCancelled();
        }
        return output.isCancelled();
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
            O transformed = Preconditions.checkNotNull(function, "transformation function")
                    .apply(result);
            output.set(transformed);
        } catch (Throwable t) {
            output.setException(t);
        }
        input = null;
        function = null;
    }

    @Override
    public void onFailure(Throwable throwable) {
        ListenableFuture<I> inputSnapshot = input;
        if (inputSnapshot != null && inputSnapshot.isCancelled()) {
            output.cancel(false);
        } else {
            output.setException(throwable);
        }
        input = null;
        function = null;
    }

    @Override
    public void failureCallback(Runnable onFailure) {
        // If null, needs to run immediately?
        input.failureCallback(onFailure);
    }
}
