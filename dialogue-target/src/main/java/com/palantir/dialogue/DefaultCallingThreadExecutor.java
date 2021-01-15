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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.logsafe.Preconditions;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.checkerframework.checker.nullness.qual.Nullable;

// Quickly hacking this together to see how it looks, likely buggy.
final class DefaultCallingThreadExecutor implements CallingThreadExecutor {

    private final Condition work = new ReentrantLock().newCondition();
    private boolean poisoned = false;
    private final BlockingDeque<RunnableFuture<?>> queue = new LinkedBlockingDeque<>();

    @Override
    public synchronized Future<?> submit(Runnable task) {
        Preconditions.checkState(!poisoned, "This queue is closed");
        RunnableFuture<?> future = new FutureTask<>(task, null);
        queue.add(future);
        work.signal();
        return future;
    }

    @Override
    public void executeQueue(ListenableFuture<?> await) {
        // TODO: This isn't using DialogueFutures cause looks like it's not a dependency.
        Futures.addCallback(
                await,
                new FutureCallback<Object>() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        work.signal();
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        work.signal();
                    }
                },
                MoreExecutors.directExecutor());

        while (true) {
            if (await.isDone()) {
                break;
            }

            RunnableFuture<?> toRun = queue.poll();
            if (toRun != null) {
                toRun.run();
            }

            waitForWork();
        }

        abortQueue();
    }

    private void waitForWork() {
        try {
            work.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            abortQueue();

            throw new DialogueException(e);
        }
    }

    private synchronized void abortQueue() {
        poisoned = true;
        RunnableFuture<?> toRun;
        while ((toRun = queue.poll()) != null) {
            toRun.cancel(false);
        }
    }
}
