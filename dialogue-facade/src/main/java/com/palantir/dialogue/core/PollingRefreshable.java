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

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates an AtomicReference from a Supplier&lt;A&gt; by eagerly applying a {@code mapFunction}
 * every second.
 *
 * This is optimized for situations where there are vastly more reads than changes to the supplier, and
 * where a 1 second propagation delay is acceptable.
 *
 * Automatically stops polling when the given {@code derivedObject} has been GC'd.
 */
@ThreadSafe
final class PollingRefreshable<A, B> implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(PollingRefreshable.class);

    private final Supplier<A> inputSupplier;
    private final AtomicReference<A> atomicInput;
    private final Function<A, B> mapFunction;
    private final WeakReference<AtomicReference<B>> sink;

    private ScheduledFuture<?> future;

    private PollingRefreshable(
            Supplier<A> inputSupplier,
            Function<A, B> mapFunction,
            ScheduledExecutorService executor,
            WeakReference<AtomicReference<B>> sink) {
        this.inputSupplier = inputSupplier;
        this.mapFunction = mapFunction;
        this.sink = sink;

        A initialInput = inputSupplier.get();
        this.atomicInput = new AtomicReference<>(initialInput);

        run(); // first run on the calling thread
        this.future = executor.scheduleWithFixedDelay(this, 1, 1, TimeUnit.SECONDS);
    }

    /** When the {@code derivedObject} is GC'd, polling will stop and this supplier will return null. */
    static <A, B> AtomicReference<B> map(
            Supplier<A> inputSupplier, Function<A, B> mapFunction, ScheduledExecutorService executor) {
        AtomicReference<B> sink = new AtomicReference<>();
        new PollingRefreshable<>(inputSupplier, mapFunction, executor, new WeakReference<>(sink));
        return sink;
    }

    private void stopPolling() {
        if (future != null) {
            future.cancel(true);

            // null out so anything we held references to can be GC'd
            future = null;
        }
    }

    @Override
    public void run() {
        AtomicReference<B> atomicSink = sink.get();
        if (atomicSink == null) {
            // when the derived weakreference has been GC'd we no longer need to update mapped
            log.warn("AtomicReference sink has been GC'd, stopping polling");
            stopPolling();
            return;
        }

        A current = atomicInput.get();
        try {
            A newInput = inputSupplier.get();
            if (Objects.equals(atomicInput, newInput)) {
                // short-circuit if the input hasn't changed, no need to run the mapFunction
                return;
            }

            B newValue = mapFunction.apply(newInput);
            if (atomicInput.compareAndSet(current, newInput)) {
                atomicSink.set(newValue);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to poll supplier and run mapFunction", e);
        }
    }
}
