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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.dialogue.blocking.DefaultCallingThreadExecutor.QueueTake;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class DefaultCallingThreadExecutorTest {

    @Mock
    private Runnable runnable1;

    @Mock
    private Runnable runnable2;

    private final SettableFuture<?> futureToAwait = SettableFuture.create();

    @Test
    public void testRunnableCompletesBeforeReturning() {
        CallingThreadExecutor executor = new DefaultCallingThreadExecutor();
        executor.submit(runnable1);
        assertThat(futureToAwait.set(null)).isTrue();
        executor.executeQueue(futureToAwait);
        verify(runnable1).run();
    }

    @Test
    public void testThrowsWhenPoisoned() {
        CallingThreadExecutor executor = new DefaultCallingThreadExecutor();
        assertThat(futureToAwait.set(null)).isTrue();
        executor.executeQueue(futureToAwait);
        Assertions.assertThatThrownBy(() -> executor.submit(() -> {}))
                .isExactlyInstanceOf(RejectedExecutionException.class);
    }

    @Test
    public void testInterruptHandling() {
        ListeningExecutorService queueExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        try {
            QueueTake queueTake = new QueueTake() {
                @Override
                public <E> E take(BlockingQueue<E> _queue) throws InterruptedException {
                    throw new InterruptedException();
                }
            };
            CallingThreadExecutor executorToUse =
                    Futures.getUnchecked(queueExecutor.submit(() -> new DefaultCallingThreadExecutor(queueTake)));

            ListenableFuture<Boolean> queueExecuted = queueExecutor.submit(() -> {
                try {
                    executorToUse.executeQueue(futureToAwait);
                    futureToAwait.get();
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return true;
                } catch (ExecutionException e) {
                    throw new UncheckedExecutionException(e);
                }
            });

            assertThat(Futures.getUnchecked(queueExecuted)).isTrue();
        } finally {
            shutdown(queueExecutor);
        }
    }

    @Test
    @SuppressWarnings({"CheckReturnValue", "FutureReturnValueIgnored"})
    public void testExecutesTasksUntilFinished() {
        ListeningExecutorService queueExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        ListeningExecutorService taskSubmitters = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        try {
            CountDownLatch latch = new CountDownLatch(2);

            // Kinda nasty because it relies on the queueExecutor not switching threads
            CallingThreadExecutor executorToUse = Futures.getUnchecked(
                    queueExecutor.submit((Callable<DefaultCallingThreadExecutor>) DefaultCallingThreadExecutor::new));
            ListenableFuture<?> queueExecuted = queueExecutor.submit(() -> executorToUse.executeQueue(futureToAwait));

            Function<Runnable, ListenableFuture<?>> submitter = task -> {
                ListenableFuture<ListenableFuture<?>> submitterFuture = taskSubmitters.submit(() -> {
                    latch.countDown();
                    Uninterruptibles.awaitUninterruptibly(latch);
                    ListenableFutureTask<?> futureTask = ListenableFutureTask.create(task, null);
                    executorToUse.submit(futureTask);
                    return futureTask;
                });

                return Futures.transformAsync(
                        submitterFuture, input -> (ListenableFuture<Object>) input, MoreExecutors.directExecutor());
            };

            ListenableFuture<?> future1 = submitter.apply(runnable1);

            RuntimeException exception = new RuntimeException();
            doThrow(exception).when(runnable2).run();
            ListenableFuture<?> future2 = submitter.apply(runnable2);

            assertThat(Futures.getUnchecked(future1)).isEqualTo(null);
            assertThatThrownBy(() -> Futures.getUnchecked(future2)).hasCause(exception);
            verify(runnable1).run();
            verify(runnable2).run();

            assertThat(futureToAwait.set(null)).isTrue();

            Futures.getUnchecked(queueExecuted);

            assertThat(queueExecuted).isDone();
        } finally {
            shutdown(queueExecutor, taskSubmitters);
        }
    }

    @Test
    @SuppressWarnings({"CheckReturnValue", "FutureReturnValueIgnored"})
    public void stressTestAllCompleteBeforeTargetFutureCompletes() {
        ListeningExecutorService queueExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        ListeningExecutorService taskSubmitters = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        try {

            CallingThreadExecutor executorToUse = Futures.getUnchecked(
                    queueExecutor.submit((Callable<DefaultCallingThreadExecutor>) DefaultCallingThreadExecutor::new));

            ListenableFuture<?> queueExecuted = queueExecutor.submit(() -> executorToUse.executeQueue(futureToAwait));

            int numElements = 1000;
            CountDownLatch allReadyToSubmit = new CountDownLatch(numElements);
            List<Integer> results = Collections.synchronizedList(new ArrayList<>(numElements));
            List<ListenableFuture<?>> futures = Collections.synchronizedList(new ArrayList<>(numElements));

            for (int i = 0; i < numElements; i++) {
                results.add(i);
            }

            for (int i = 0; i < numElements; i++) {
                final int iValue = i;
                ListenableFuture<ListenableFuture<?>> submitterFuture = taskSubmitters.submit(() -> {
                    allReadyToSubmit.countDown();
                    Uninterruptibles.awaitUninterruptibly(allReadyToSubmit);
                    ListenableFutureTask<?> task =
                            ListenableFutureTask.create(() -> results.set(iValue, -iValue), null);
                    executorToUse.submit(task);
                    return task;
                });
                futures.add(Futures.transformAsync(
                        submitterFuture, input -> (ListenableFuture<Object>) input, MoreExecutors.directExecutor()));
            }

            Futures.getUnchecked(Futures.allAsList(futures));

            assertThat(futureToAwait.set(null)).isTrue();

            Futures.getUnchecked(queueExecuted);

            for (int i = 0; i < numElements; i++) {
                assertThat(results.get(i)).isEqualTo(-i);
            }
        } finally {
            shutdown(queueExecutor, taskSubmitters);
        }
    }

    private static void shutdown(ExecutorService... executors) {
        boolean failed = false;
        for (ExecutorService executor : executors) {
            if (!MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(3))) {
                failed = true;
            }
        }
        if (failed) {
            throw new SafeIllegalStateException("Failed to shut down all executors");
        }
    }
}
