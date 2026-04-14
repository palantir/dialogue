/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestAttachmentKey;
import javax.annotation.Nullable;

/**
 * Attachment for sharing a queue timeout expiration across both the channel-level and endpoint-level
 * {@link QueuedChannel} instances. The first queue to enqueue a request stamps an absolute expiration time (nanos from
 * {@link com.github.benmanes.caffeine.cache.Ticker#read()}). The second queue reads the existing expiration and uses
 * the remaining budget.
 */
final class QueueTimeoutAttachments {
    private static final RequestAttachmentKey<Long> QUEUE_EXPIRATION_NANOS = RequestAttachmentKey.create(Long.class);
    private static final long CLEARED_SENTINEL = Long.MIN_VALUE;

    private QueueTimeoutAttachments() {}

    /**
     * Clears the expiration attachment by setting it to a sentinel value that {@link #setExpirationIfAbsent} treats as
     * absent.
     */
    static void clearExpiration(Request request) {
        request.attachments().put(QUEUE_EXPIRATION_NANOS, CLEARED_SENTINEL);
    }

    /** Sets the expiration only if one hasn't been set yet (or was cleared). */
    static long setExpirationIfAbsent(Request request, long expirationNanos) {
        Long existing = request.attachments().getOrDefault(QUEUE_EXPIRATION_NANOS, null);
        if (existing != null && existing != CLEARED_SENTINEL) {
            return existing;
        }
        request.attachments().put(QUEUE_EXPIRATION_NANOS, expirationNanos);
        return expirationNanos;
    }

    @VisibleForTesting
    static @Nullable Long getExpiration(Request request) {
        Long value = request.attachments().getOrDefault(QUEUE_EXPIRATION_NANOS, null);
        return (value != null && value != CLEARED_SENTINEL) ? value : null;
    }
}
