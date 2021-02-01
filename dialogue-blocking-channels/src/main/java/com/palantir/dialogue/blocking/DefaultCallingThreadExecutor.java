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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.palantir.dialogue.RequestAttachmentKey;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultCallingThreadExecutor implements CallingThreadExecutor {

    static final RequestAttachmentKey<CallingThreadExecutor> ATTACHMENT_KEY =
            RequestAttachmentKey.create(CallingThreadExecutor.class);

    private static final Logger log = LoggerFactory.getLogger(DefaultCallingThreadExecutor.class);
    private static final boolean DO_NOT_INTERRUPT = false;
    private final long threadId = Thread.currentThread().getId();
    private final Queue queue;

    @VisibleForTesting
    interface QueueTake {
        <E> E take(BlockingQueue<E> queue) throws InterruptedException;
    }

    @VisibleForTesting
    DefaultCallingThreadExecutor(QueueTake queueTake) {
        queue = new Queue(queueTake);
    }

    DefaultCallingThreadExecutor() {
        this(BlockingQueue::take);
    }

    @Override
    public ListenableFuture<?> submit(Runnable task) {
        return queue.submit(task);
    }

    @Override
    public void executeQueue(ListenableFuture<?> await) {
        Preconditions.checkState(Thread.currentThread().getId() == threadId, "Executing queue on different thread");
        await.addListener(() -> queue.submitNotifier(queue::poison), DialogueFutures.safeDirectExecutor());
        try {
            RunnableFuture<?> toRun;
            while ((toRun = queue.getWork()) != null) {
                toRun.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            abortQueue();

            await.cancel(DO_NOT_INTERRUPT);
        }
    }

    private void abortQueue() {
        queue.poison();

        RunnableFuture<?> toRun;
        while ((toRun = queue.poll()) != null) {
            toRun.cancel(DO_NOT_INTERRUPT);
        }
    }

    private static final class Queue {
        private boolean poisoned = false;
        private final BlockingQueue<RunnableFuture<?>> queue = new LinkedBlockingQueue<>();
        private final QueueTake queueTake;

        Queue(QueueTake take) {
            this.queueTake = take;
        }

        public synchronized ListenableFuture<?> submit(Runnable task) {
            checkNotPoisoned();
            return addTask(task);
        }

        public synchronized void submitNotifier(Runnable task) {
            if (poisoned) {
                return;
            }
            addTask(task);
        }

        public synchronized void poison() {
            poisoned = true;
        }

        public RunnableFuture<?> getWork() throws InterruptedException {
            if (!isPoisoned()) {
                return queueTake.take(queue);
            } else {
                return queue.poll();
            }
        }

        public RunnableFuture<?> poll() {
            return queue.poll();
        }

        private synchronized void checkNotPoisoned() {
            if (poisoned) {
                log.info("Submitted task after queue is closed");
                throw new RejectedExecutionException("Queue closed");
            }
        }

        private synchronized boolean isPoisoned() {
            return poisoned;
        }

        private synchronized ListenableFuture<?> addTask(Runnable task) {
            ListenableFutureTask<?> future = ListenableFutureTask.create(task, null);
            queue.add(future);
            return future;
        }
    }
}
