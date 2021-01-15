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

package com.palantir.dialogue;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

final class DefaultCallingThreadExecutor implements CallingThreadExecutor {

    private final Deque<RunnableFuture<?>> queue = new ConcurrentLinkedDeque<>();

    @Override
    public Future<?> submit(Runnable task) {
        RunnableFuture<?> future = new FutureTask<>(task, null);
        queue.add(future);
        return future;
    }

    @Override
    public void executeQueue() {
        RunnableFuture<?> toRun;
        while ((toRun = queue.poll()) != null) {
            toRun.run();
        }
    }
}
