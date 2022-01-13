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

import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

final class Attachments {
    @SuppressWarnings("DangerousIdentityKey")
    private final Map<AttachmentKey<?>, Object> attachments = new ConcurrentHashMap<>(0);

    private Attachments() {}

    static Attachments create() {
        return new Attachments();
    }

    @Nullable
    <V> V put(AttachmentKey<V> key, V value) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(value, "value");
        key.checkIsInstance(value);
        return (V) attachments.put(key, value);
    }

    @Nullable
    <V> V getOrDefault(AttachmentKey<V> key, @Nullable V defaultValue) {
        Preconditions.checkNotNull(key, "key");
        return (V) attachments.getOrDefault(key, defaultValue);
    }

    static final class AttachmentKey<V> {

        private final Class<V> valueClazz;

        private AttachmentKey(Class<V> valueClazz) {
            this.valueClazz = valueClazz;
        }

        private void checkIsInstance(V value) {
            if (!valueClazz.isInstance(value)) {
                throw new SafeIllegalArgumentException(
                        "Unexpected type",
                        SafeArg.of("expected", valueClazz),
                        SafeArg.of("actualType", value == null ? null : value.getClass()));
            }
        }
    }

    @SuppressWarnings({"unchecked", "RawTypes"})
    static <T> AttachmentKey<T> createAttachmentKey(Class<? super T> valueClazz) {
        Preconditions.checkNotNull(valueClazz, "valueClazz");
        return new AttachmentKey(valueClazz);
    }
}
