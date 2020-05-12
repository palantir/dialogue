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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.function.Consumer;

final class DialogueFutures {
    private DialogueFutures() {}

    @CanIgnoreReturnValue
    static <T> ListenableFuture<T> addDirectCallback(ListenableFuture<T> future, FutureCallback<T> callback) {
        Futures.addCallback(future, callback, MoreExecutors.directExecutor());
        return future;
    }

    @CanIgnoreReturnValue
    static <T> ListenableFuture<T> addDirectListener(ListenableFuture<T> future, Runnable listener) {
        future.addListener(listener, MoreExecutors.directExecutor());
        return future;
    }

    static <T> FutureCallback<T> onSuccess(Consumer<T> onSuccess) {
        return new FutureCallback<T>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(Throwable _throwable) {}
        };
    }
}
