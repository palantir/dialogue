/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Returns the result of applying the given function to the result of calling {@link Supplier#get()}}, only reapplying
 * the function when the value returned from {@code get()} changes.
 */
final class MemoizingComposingSupplier<T, V> implements Supplier<V> {

    private final Supplier<T> delegate;
    private final Function<T, V> function;

    @Nullable
    private volatile T input = null;

    @Nullable
    private volatile V result = null;

    MemoizingComposingSupplier(Supplier<T> delegate, Function<T, V> function) {
        this.delegate = delegate;
        this.function = function;
    }

    @Override
    @Nullable
    public V get() {
        if (!delegate.get().equals(input)) {
            synchronized (this) {
                T newInput = delegate.get();
                if (!newInput.equals(input)) {
                    input = newInput;
                    result = function.apply(newInput);
                }
            }
        }
        return result;
    }
}
