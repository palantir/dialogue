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
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultCallingThreadExecutor implements CallingThreadExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultCallingThreadExecutor.class);
    private final long threadId = Thread.currentThread().getId();
    private final Queue queue = new Queue();

    /** Notification when main future completes. */
    private final Runnable notifier = queue::poison;

    @Override
    public synchronized Future<?> submit(Runnable task) {
        if (Thread.currentThread().getId() == threadId) {
            RunnableFuture<?> future = new FutureTask<>(task, null);
            future.run();
            return future;
        }

        return queue.submit(task);
    }

    @Override
    public void executeQueue(ListenableFuture<?> await) {
        Preconditions.checkState(Thread.currentThread().getId() == threadId, "Executing queue on different thread");
        Futures.addCallback(
                await,
                new FutureCallback<Object>() {
                    @Override
                    @SuppressWarnings("FutureReturnValueIgnored")
                    public void onSuccess(Object _result) {
                        queue.submit(notifier);
                    }

                    @Override
                    @SuppressWarnings("FutureReturnValueIgnored")
                    public void onFailure(Throwable _throwable) {
                        queue.submit(notifier);
                    }
                },
                DialogueFutures.safeDirectExecutor());

        RunnableFuture<?> toRun;
        while ((toRun = queue.getWork()) != null) {
            toRun.run();
        }
    }

    private static final class Queue {
        private boolean poisoned = false;
        private final BlockingDeque<RunnableFuture<?>> queue = new LinkedBlockingDeque<>();

        public synchronized Future<?> submit(Runnable task) {
            if (poisoned) {
                log.info("Submitted task after queue is closed");
                throw new RejectedExecutionException("Queue closed");
            }
            RunnableFuture<?> future = new FutureTask<>(task, null);
            queue.add(future);
            return future;
        }

        public synchronized void poison() {
            poisoned = true;
        }

        public RunnableFuture<?> getWork() {
            try {
                if (!isPoisoned()) {
                    return queue.take();
                } else {
                    return queue.poll();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                abortQueue();

                throw new DialogueException(e);
            }
        }

        private synchronized boolean isPoisoned() {
            return poisoned;
        }

        private synchronized void abortQueue() {
            RunnableFuture<?> toRun;
            while ((toRun = queue.poll()) != null) {
                toRun.cancel(false);
            }
        }
    }
}
