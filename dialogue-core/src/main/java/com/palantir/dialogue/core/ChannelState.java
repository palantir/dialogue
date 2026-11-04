/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.logsafe.Preconditions;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

final class ChannelState {
    static final class Key<T> {
        private final Class<T> valueClass;

        private T cast(final Object value) {
            return valueClass.cast(value);
        }

        Key(final Class<T> valueClass) {
            this.valueClass = valueClass;
        }
    }

    @SuppressWarnings("DangerousIdentityKey")
    private final Map<Key<?>, Object> state = new HashMap<>();

    /** Returns the state stored for {@code key}, creating it with {@code factory} if absent. */
    <T> T getState(Key<T> key, Supplier<T> factory) {
        return key.cast(Preconditions.checkNotNull(
                state.computeIfAbsent(key, _key -> factory.get()), "state factory cannot produce a null value"));
    }
}
