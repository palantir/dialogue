/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.blocking;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.function.Consumer;

final class ResilientCallingThreadExecutor implements CallingThreadExecutor {

    private final CallingThreadExecutor delegate;
    private final Consumer<RuntimeException> failureConsumer;

    ResilientCallingThreadExecutor(CallingThreadExecutor delegate, Consumer<RuntimeException> failureConsumer) {
        this.delegate = delegate;
        this.failureConsumer = failureConsumer;
    }

    @Override
    public void execute(Runnable task) {
        try {
            delegate.execute(task);
        } catch (RuntimeException e) {
            // RejectedExecutionException is still a failure, as it suggests we have some bad assumptions.
            failureConsumer.accept(e);
            throw e;
        }
    }

    @Override
    public void executeQueue(ListenableFuture<?> await) {
        try {
            delegate.executeQueue(await);
        } catch (RuntimeException e) {
            failureConsumer.accept(e);
            throw e;
        }
    }
}
