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

import javax.annotation.Nullable;

public final class RequestAttachments {

    private final Attachments attachments = Attachments.create();

    private RequestAttachments() {}

    static RequestAttachments create() {
        return new RequestAttachments();
    }

    @Nullable
    public <V> V put(RequestAttachmentKey<V> key, V value) {
        return attachments.put(key.attachment(), value);
    }

    @Nullable
    public <V> V getOrDefault(RequestAttachmentKey<V> key, @Nullable V defaultValue) {
        return attachments.getOrDefault(key.attachment(), defaultValue);
    }
}
