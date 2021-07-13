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

import com.palantir.dialogue.Attachments.AttachmentKey;

public final class ResponseAttachmentKey<V> {

    private final AttachmentKey<V> attachment;

    private ResponseAttachmentKey(AttachmentKey<V> attachment) {
        this.attachment = attachment;
    }

    AttachmentKey<V> attachment() {
        return attachment;
    }

    public static <T> ResponseAttachmentKey<T> create(Class<? super T> valueClazz) {
        return new ResponseAttachmentKey<>(Attachments.createAttachmentKey(valueClazz));
    }
}
