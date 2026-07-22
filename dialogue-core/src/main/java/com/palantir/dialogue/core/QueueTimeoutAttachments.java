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
    private static final RequestAttachmentKey<DeadlineExpiration> DEADLINE_EXPIRATION =
            RequestAttachmentKey.create(DeadlineExpiration.class);

    private QueueTimeoutAttachments() {}

    /** Clears the expiration so the next queue stamps a fresh budget (used on retry). */
    static void clearExpiration(Request request) {
        request.attachments().remove(QUEUE_EXPIRATION_NANOS);
    }

    /** Sets the expiration only if one hasn't been set yet. */
    static void setExpirationIfAbsent(Request request, long expirationNanos) {
        request.attachments().putIfAbsent(QUEUE_EXPIRATION_NANOS, expirationNanos);
    }

    @VisibleForTesting
    static @Nullable Long getExpiration(Request request) {
        return request.attachments().getOrDefault(QUEUE_EXPIRATION_NANOS, null);
    }

    static void setDeadlineExpirationIfAbsent(Request request, @Nullable Long expirationNanos) {
        request.attachments().putIfAbsent(DEADLINE_EXPIRATION, new DeadlineExpiration(expirationNanos));
    }

    static boolean isDeadlineResolved(Request request) {
        return request.attachments().getOrDefault(DEADLINE_EXPIRATION, null) != null;
    }

    static @Nullable Long getDeadlineExpiration(Request request) {
        DeadlineExpiration expiration = request.attachments().getOrDefault(DEADLINE_EXPIRATION, null);
        return expiration == null ? null : expiration.expirationNanos();
    }

    // The wrapper distinguishes an unresolved deadline from a resolved request with no deadline.
    private record DeadlineExpiration(@Nullable Long expirationNanos) {}
}
