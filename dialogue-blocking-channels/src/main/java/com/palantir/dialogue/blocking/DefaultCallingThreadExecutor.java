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
import com.palantir.dialogue.RequestAttachmentKey;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultCallingThreadExecutor implements CallingThreadExecutor {

    static final RequestAttachmentKey<CallingThreadExecutor> ATTACHMENT_KEY =
            RequestAttachmentKey.create(CallingThreadExecutor.class);

    private static final Logger log = LoggerFactory.getLogger(DefaultCallingThreadExecutor.class);
    private static final boolean DO_NOT_INTERRUPT = false;
    private final long threadId = Thread.currentThread().getId();
    private final Queue queue;

    DefaultCallingThreadExecutor() {
        queue = new Queue();
    }

    @Override
    public void submit(Runnable task) {
        queue.submit(task);
    }

    @Override
    public void executeQueue(ListenableFuture<?> await) {
        Preconditions.checkState(Thread.currentThread().getId() == threadId, "Executing queue on different thread");
        DialogueFutures.addDirectListener(await, () -> queue.submitNotifier(queue::poison));
        try {
            Runnable toRun;
            while ((toRun = queue.getWork()) != null) {
                try {
                    toRun.run();
                } catch (Throwable t) {
                    // This should never happen, BlockingChannelAdapter uses a SettableFuture as output
                    // and is not expected to allow throwables to escape.
                    log.error("Failed to execute runnable {}", SafeArg.of("runnable", toRun), t);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            queue.poison();

            await.cancel(DO_NOT_INTERRUPT);
        }
    }

    private static final class Queue {
        private boolean poisoned = false;
        private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

        public synchronized void submit(Runnable task) {
            checkNotPoisoned();
            addTask(task);
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        public synchronized void submitNotifier(Runnable task) {
            if (poisoned) {
                return;
            }
            addTask(task);
        }

        public synchronized void poison() {
            poisoned = true;
        }

        public Runnable getWork() throws InterruptedException {
            if (!isPoisoned()) {
                return queue.take();
            } else {
                return queue.poll();
            }
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

        private synchronized void addTask(Runnable task) {
            queue.add(task);
        }
    }
}
