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

package com.palantir.dialogue.core;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jmock.lib.concurrent.DeterministicExecutor;
import org.jmock.lib.concurrent.UnsupportedSynchronousOperationException;
import org.jmock.lib.concurrent.internal.DeltaQueue;

/**
 * Modified from https://github.com/jmock-developers/jmock-library/blob/498d09a015205f1370bf3855d59db033cf541c3c/jmock/src/main/java/org/jmock/lib/concurrent/DeterministicScheduler.java
 * Modification is proposed upstream:
 * https://github.com/jmock-developers/jmock-library/issues/172
 * https://github.com/jmock-developers/jmock-library/pull/173
 *
 * Copyright (c) 2000-2017, jMock.org
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. Redistributions
 * in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * Neither the name of jMock nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * A {@link ScheduledExecutorService} that executes commands on the thread that calls
 * {@link #runNextPendingCommand() runNextPendingCommand}, {@link #runUntilIdle() runUntilIdle} or
 * {@link #tick(long, TimeUnit) tick}.  Objects of this class can also be used
 * as {@link Executor}s or {@link ExecutorService}s if you just want to control background execution
 * and don't need to schedule commands, but it may be simpler to use a {@link DeterministicExecutor}.
 *
 * @author nat
 */
public final class NanosecondPrecisionDeterministicScheduler implements ScheduledExecutorService {
    private final DeltaQueue<ScheduledTask<?>> deltaQueue = new DeltaQueue<>();

    /**
     * Runs time forwards by a given duration, executing any commands scheduled for
     * execution during that time period, and any background tasks spawned by the
     * scheduled tasks.  Therefore, when a call to tick returns, the executor
     * will be idle.
     */
    public void tick(long duration, TimeUnit timeUnit) {
        long remaining = toTicks(duration, timeUnit);

        do {
            remaining = deltaQueue.tick(remaining);
            runUntilIdle();

        } while (deltaQueue.isNotEmpty() && remaining > 0);
    }

    /**
     * Runs all commands scheduled to be executed immediately but does
     * not tick time forward.
     */
    public void runUntilIdle() {
        while (!isIdle()) {
            runNextPendingCommand();
        }
    }

    /**
     * Runs the next command scheduled to be executed immediately.
     */
    public void runNextPendingCommand() {
        ScheduledTask<?> scheduledTask = deltaQueue.pop();

        scheduledTask.run();

        if (!scheduledTask.isCancelled() && scheduledTask.repeats()) {
            deltaQueue.add(scheduledTask.repeatDelay, scheduledTask);
        }
    }

    /**
     * Reports whether scheduler is "idle": has no commands pending immediate execution.
     *
     * @return true if there are no commands pending immediate execution,
     *         false if there are commands pending immediate execution.
     */
    public boolean isIdle() {
        return deltaQueue.isEmpty() || deltaQueue.delay() > 0;
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void execute(Runnable command) {
        schedule(command, 0, TimeUnit.SECONDS);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        ScheduledTask<Void> task = new ScheduledTask<>(command);
        deltaQueue.add(toTicks(delay, unit), task);
        return task;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ScheduledTask<V> task = new ScheduledTask<V>(callable);
        deltaQueue.add(toTicks(delay, unit), task);
        return task;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return scheduleWithFixedDelay(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        ScheduledTask<Object> task = new ScheduledTask<>(toTicks(delay, unit), command);
        deltaQueue.add(toTicks(initialDelay, unit), task);
        return task;
    }

    @Override
    public boolean awaitTermination(long _timeout, TimeUnit _unit) {
        throw blockingOperationsNotSupported();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> _tasks) {
        throw blockingOperationsNotSupported();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> _tasks, long _timeout, TimeUnit _unit)
            throws InterruptedException {
        throw blockingOperationsNotSupported();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> _tasks) {
        throw blockingOperationsNotSupported();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> _tasks, long _timeout, TimeUnit _unit) {
        throw blockingOperationsNotSupported();
    }

    @Override
    public boolean isShutdown() {
        throw shutdownNotSupported();
    }

    @Override
    public boolean isTerminated() {
        throw shutdownNotSupported();
    }

    @Override
    public void shutdown() {
        throw shutdownNotSupported();
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw shutdownNotSupported();
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        return schedule(callable, 0, TimeUnit.SECONDS);
    }

    @Override
    public Future<?> submit(Runnable command) {
        return submit(command, null);
    }

    @Override
    public <T> Future<T> submit(Runnable command, T result) {
        return submit(new CallableRunnableAdapter<T>(command, result));
    }

    private static final class CallableRunnableAdapter<T> implements Callable<T> {
        private final Runnable runnable;
        private final T result;

        CallableRunnableAdapter(Runnable runnable, T result) {
            this.runnable = runnable;
            this.result = result;
        }

        @Override
        public String toString() {
            return runnable.toString();
        }

        @Override
        public T call() {
            runnable.run();
            return result;
        }
    }

    private final class ScheduledTask<T> implements ScheduledFuture<T>, Runnable {
        private final long repeatDelay;
        private final Callable<T> command;
        private boolean isCancelled = false;
        private boolean isDone = false;
        private T futureResult;
        private Exception failure = null;

        ScheduledTask(Callable<T> command) {
            this.repeatDelay = -1;
            this.command = command;
        }

        ScheduledTask(Runnable command) {
            this(-1, command);
        }

        ScheduledTask(long repeatDelay, Runnable command) {
            this.repeatDelay = repeatDelay;
            this.command = new CallableRunnableAdapter<T>(command, null);
        }

        @Override
        public String toString() {
            return command.toString() + " repeatDelay=" + repeatDelay;
        }

        public boolean repeats() {
            return repeatDelay >= 0;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(Duration.ofNanos(deltaQueue.delay(this)));
        }

        @Override
        public int compareTo(Delayed _object) {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public boolean cancel(boolean _mayInterruptIfRunning) {
            isCancelled = true;
            return deltaQueue.remove(this);
        }

        @Override
        public T get() throws ExecutionException {
            if (!isDone) {
                throw blockingOperationsNotSupported();
            }

            if (failure != null) {
                throw new ExecutionException(failure);
            }

            return futureResult;
        }

        @Override
        public T get(long _timeout, TimeUnit _unit) throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }

        @Override
        public boolean isCancelled() {
            return isCancelled;
        }

        @Override
        public boolean isDone() {
            return isDone;
        }

        @Override
        public void run() {
            try {
                futureResult = command.call();
            } catch (Exception e) {
                failure = e;
            }
            isDone = true;
        }
    }

    private long toTicks(long duration, TimeUnit timeUnit) {
        return TimeUnit.NANOSECONDS.convert(duration, timeUnit);
    }

    private UnsupportedSynchronousOperationException blockingOperationsNotSupported() {
        return new UnsupportedSynchronousOperationException("cannot perform blocking wait on a task scheduled on a "
                + NanosecondPrecisionDeterministicScheduler.class.getName());
    }

    private UnsupportedOperationException shutdownNotSupported() {
        return new UnsupportedOperationException("shutdown not supported");
    }
}
