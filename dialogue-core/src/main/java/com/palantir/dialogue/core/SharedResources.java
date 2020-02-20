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

import java.io.Closeable;
import java.util.function.Function;

/** A mutable central store where we can create expensive things once. Inspired by JUnit5's ExtensionContext.Store. */
public interface SharedResources extends Closeable {

    Store getStore(String namespace);

    interface Store {
        <K, V extends Closeable> V getOrComputeIfAbsent(K key, Function<K, V> defaultCreator, Class<V> requiredType);
    }
}
