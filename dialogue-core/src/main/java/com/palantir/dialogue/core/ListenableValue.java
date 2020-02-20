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

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Preconditions;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ListenableValue<T> implements Listenable<T> {
    private static final Logger log = LoggerFactory.getLogger(ListenableValue.class);

    private final AtomicReference<ImmutableList<Runnable>> listeners = new AtomicReference<>(ImmutableList.of());
    private volatile T value;

    ListenableValue(T value) {
        this.value = Preconditions.checkNotNull(value, "initial value");
    }

    @Override
    public T getListenableCurrentValue() {
        return value;
    }

    void setValue(T newValue) {
        this.value = newValue;
        for (Runnable runnable : listeners.get()) {
            try {
                runnable.run();
            } catch (RuntimeException e) {
                log.error("Failed to notify subscriber", e);
            }
        }
    }

    @Override
    public Subscription subscribe(Runnable updateListener) {
        for (ImmutableList<Runnable> items = listeners.get();
                !listeners.compareAndSet(items, add(items, updateListener));
                items = listeners.get()) {
            /* empty */
        }

        return new Subscription() {
            @Override
            public void close() {
                for (ImmutableList<Runnable> items = listeners.get();
                        !listeners.compareAndSet(items, remove(items, updateListener));
                        items = listeners.get()) {
                    /* empty */
                }
            }
        };
    }

    private static <T> ImmutableList<T> add(List<T> existing, T updateListener) {
        return ImmutableList.<T>builder().addAll(existing).add(updateListener).build();
    }

    private static <T> ImmutableList<T> remove(List<T> existing, T updateListener) {
        return existing.stream().filter(item -> item != updateListener).collect(ImmutableList.toImmutableList());
    }
}
