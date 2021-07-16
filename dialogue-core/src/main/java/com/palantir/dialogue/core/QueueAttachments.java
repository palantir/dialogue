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

package com.palantir.dialogue.core;

import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestAttachmentKey;
import javax.annotation.CheckForNull;

final class QueueAttachments {
    static final RequestAttachmentKey<Channel> QUEUE_OVERRIDE = RequestAttachmentKey.create(Channel.class);

    private QueueAttachments() {}

    static void setQueueOverride(Request request, Channel channel) {
        request.attachments().put(QueueAttachments.QUEUE_OVERRIDE, channel);
    }

    @CheckForNull
    static Channel getQueueOverride(Request request) {
        return request.attachments().getOrDefault(QueueAttachments.QUEUE_OVERRIDE, null);
    }
}
