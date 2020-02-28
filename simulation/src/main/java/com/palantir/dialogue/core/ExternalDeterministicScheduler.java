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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ExternalDeterministicScheduler implements ListeningScheduledExecutorService {

    private final NanosecondPrecisionDeterministicScheduler deterministicExecutor;
    private final ListeningScheduledExecutorService delegate;
    private final TestCaffeineTicker ticker;

    ExternalDeterministicScheduler(
            NanosecondPrecisionDeterministicScheduler deterministicExecutor, TestCaffeineTicker ticker) {
        this.deterministicExecutor = deterministicExecutor;
        this.delegate = MoreExecutors.listeningDecorator(deterministicExecutor);
        this.ticker = ticker;
    }

    @Override
    public ListenableScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return schedule(
                () -> {
                    command.run();
                    return null;
                },
                delay,
                unit);
    }

    @Override
    public <V> ListenableScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
        deterministicExecutor.tick(0, TimeUnit.NANOSECONDS);
        long scheduleTime = ticker.read();
        long delayNanos = Math.max(0L, unit.toNanos(delay));
        try {
            return delegate.schedule(
                    () -> {
                        ticker.advanceTo(scheduleTime + delayNanos);
                        return command.call();
                    },
                    delayNanos,
                    TimeUnit.NANOSECONDS);
        } finally {
            deterministicExecutor.tick(0, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public ListenableScheduledFuture<?> scheduleAtFixedRate(
            Runnable _command, long _initialDelay, long _period, TimeUnit _unit) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ListenableScheduledFuture<?> scheduleWithFixedDelay(
            Runnable _command, long _initialDelay, long _delay, TimeUnit _unit) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("Cannot shut down");
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException("Cannot shut down");
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> ListenableFuture<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    @Override
    public <T> ListenableFuture<T> submit(Runnable task, T result) {
        return delegate.submit(task, result);
    }

    @Override
    public ListenableFuture<?> submit(Runnable task) {
        return delegate.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }
}
