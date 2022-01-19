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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Internal utility functionality used by Dialogue modules.
 *
 * The transformation methods do not leak closeable resources when cancel is called while the function executes.
 * @see <a href="https://github.com/google/guava/issues/3975">guava#3975</a>
 */
public final class DialogueFutures {

    /**
     * Safely transform the result of an input future using the provided function. The function is executed on
     * a direct executor.
     *
     * @see DialogueDirectTransformationFuture
     */
    public static <I, O> DialogueListenableFuture<O> transform(
            ListenableFuture<I> input, Function<? super I, ? extends O> function) {
        // I don't want to touch all the code in this PR, so just going to do instanceof.
        if (input instanceof DialogueListenableFuture) {
            return new DialogueDirectTransformationFuture<>((DialogueListenableFuture<I>) input, function);
        } else {
            return new DialogueDirectTransformationFuture<>(wrap(input), function);
        }
    }

    public static <I, O> ListenableFuture<O> transformAsync(
            ListenableFuture<I> input, AsyncFunction<? super I, ? extends O> function) {
        return new DialogueDirectAsyncTransformationFuture<>(input, function);
    }

    public static <T> ListenableFuture<T> catchingAllAsync(
            ListenableFuture<T> input, AsyncFunction<Throwable, T> function) {
        return new DialogueDirectAsyncCatchingFuture<T>(input, function);
    }

    @CanIgnoreReturnValue
    public static <T> ListenableFuture<T> addDirectCallback(ListenableFuture<T> future, FutureCallback<T> callback) {
        Futures.addCallback(future, callback, safeDirectExecutor());
        return future;
    }

    @CanIgnoreReturnValue
    public static <T> ListenableFuture<T> addDirectListener(ListenableFuture<T> future, Runnable listener) {
        future.addListener(listener, safeDirectExecutor());
        return future;
    }

    public static Executor safeDirectExecutor() {
        return SafeDirectExecutor.INSTANCE;
    }

    public static <T> FutureCallback<T> onSuccess(Consumer<T> onSuccess) {
        return new FutureCallback<T>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(Throwable _throwable) {}
        };
    }

    public static <V> DialogueListenableFuture<V> immediateFuture(V value) {
        return wrap(Futures.immediateFuture(value));
    }

    public static <V> DialogueListenableFuture<V> immediateFailedFuture(Throwable throwable) {
        return wrap(Futures.immediateFailedFuture(throwable));
    }

    private DialogueFutures() {}

    private static <V> DialogueListenableFuture<V> wrap(ListenableFuture<V> input) {
        return new BaseDialogueListenableFuture<>(input);
    }

    private static class BaseDialogueListenableFuture<V> implements DialogueListenableFuture<V>, Runnable {

        @Nullable
        private ListenableFuture<V> input;

        private final ResourceContext ctx;

        private BaseDialogueListenableFuture(ListenableFuture<V> input) {
            ctx = ResourceContext.createEmpty();
            this.input = Futures.catchingAsync(
                    input,
                    Throwable.class,
                    throwable -> {
                        try {
                            ctx.close();
                            return input;
                        } catch (RuntimeException e) {
                            throwable.addSuppressed(e);
                            return Futures.immediateFailedFuture(throwable);
                        }
                    },
                    DialogueFutures.safeDirectExecutor());
            input.addListener(this, DialogueFutures.safeDirectExecutor());
        }

        @Override
        public void failureCallback(Runnable onFailure) {
            ctx.onClose(onFailure);
        }

        @Override
        public void addListener(Runnable listener, Executor executor) {
            input.addListener(listener, executor);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return input.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return input.isCancelled();
        }

        @Override
        public boolean isDone() {
            return input.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return input.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return input.get(timeout, unit);
        }

        @Override
        public void run() {
            this.input = null;
        }
    }
}
