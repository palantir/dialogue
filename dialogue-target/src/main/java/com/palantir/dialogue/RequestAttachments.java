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

import com.google.common.annotations.Beta;
import com.palantir.logsafe.Preconditions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

@Beta
public final class RequestAttachments {

    private final Map<RequestAttachmentKey<?>, Object> attachments = new ConcurrentHashMap<>(0);

    private RequestAttachments() {}

    static RequestAttachments create() {
        return new RequestAttachments();
    }

    @Nullable
    public <V> V put(RequestAttachmentKey<V> key, V value) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(value, "value");
        key.checkIsInstance(value);
        return (V) attachments.put(key, value);
    }

    @Nullable
    public <V> V getOrDefault(RequestAttachmentKey<V> key, @Nullable V defaultValue) {
        Preconditions.checkNotNull(key, "key");
        return (V) attachments.getOrDefault(key, defaultValue);
    }
}
