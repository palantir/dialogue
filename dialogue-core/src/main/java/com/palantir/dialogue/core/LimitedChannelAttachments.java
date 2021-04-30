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

import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestAttachmentKey;
import java.util.UUID;

public final class LimitedChannelAttachments {

    private static final RequestAttachmentKey<UUID> LIMITER_KEY = RequestAttachmentKey.create(UUID.class);
    private static final UUID GLOBAL_QUEUE = UUID.randomUUID();

    private LimitedChannelAttachments() {}

    public static void addLimitingKey(Request request, UUID value) {
        request.attachments().put(LIMITER_KEY, value);
    }

    @SuppressWarnings("NullAway")
    static UUID getLimitingKeyOrDefault(Request request) {
        return request.attachments().getOrDefault(LIMITER_KEY, GLOBAL_QUEUE);
    }
}
