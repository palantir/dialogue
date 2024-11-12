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
        private final Supplier<T> factory;

        T cast(final Object value) {
            return valueClass.cast(value);
        }

        Supplier<T> getFactory() {
            return factory;
        }

        Key(final Class<T> valueClass, Supplier<T> factory) {
            this.valueClass = valueClass;
            this.factory = factory;
        }
    }

    @SuppressWarnings("DangerousIdentityKey")
    private final Map<Key<?>, Object> state = new HashMap<>();

    <T> T getState(Key<T> key) {
        if (state.containsKey(key)) {
            return key.cast(state.get(key));
        } else {
            T value = key.getFactory().get();
            Preconditions.checkNotNull(value, "state factory cannot produce a null value");
            state.put(key, value);
            return value;
        }
    }
}
