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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class DefaultCallingThreadExecutorTest {

    @Mock
    private Runnable runnable;

    private final SettableFuture<?> futureToAwait = SettableFuture.create();

    private final CallingThreadExecutor executor = new DefaultCallingThreadExecutor();

    @Test
    public void testRunnableCompletesBeforeReturning() {
        Future<?> future1 = executor.submit(runnable);
        futureToAwait.set(null);
        executor.executeQueue(futureToAwait);
        verify(runnable).run();
        assertThat(future1).isDone();
    }

    @Test
    public void testThrowsWhenPoisoned() {
        futureToAwait.set(null);
        executor.executeQueue(futureToAwait);
        Assertions.assertThatThrownBy(() -> executor.submit(() -> {}))
                .isExactlyInstanceOf(RejectedExecutionException.class);
    }

    @Test
    public void testExecutesTasksUntilFinished() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<?> queueExecuted = executorService.submit(() -> executor.executeQueue(futureToAwait));

        Future<?> future1 = executor.submit(runnable);
        Future<?> future2 = executor.submit(runnable);
        futureToAwait.set(null);

        verify(runnable, times(2)).run();
        assertThat(queueExecuted).isDone();
        assertThat(future1).isDone();
        assertThat(future2).isDone();
    }
}
