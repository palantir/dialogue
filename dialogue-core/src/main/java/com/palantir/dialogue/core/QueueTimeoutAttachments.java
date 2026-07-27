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

/** Shares configured queue-timeout and deadline expirations across {@link QueuedChannel} instances. */
final class QueueTimeoutAttachments {
    private static final RequestAttachmentKey<Long> CONFIGURED_EXPIRATION_NANOS =
            RequestAttachmentKey.create(Long.class);
    private static final RequestAttachmentKey<Long> DEADLINE_EXPIRATION_NANOS = RequestAttachmentKey.create(Long.class);

    private QueueTimeoutAttachments() {}

    /** Clears the configured expiration so the next queue stamps a fresh budget (used on retry). */
    static void clearConfiguredExpiration(Request request) {
        request.attachments().remove(CONFIGURED_EXPIRATION_NANOS);
    }

    /** Returns the configured expiration, initializing it to the candidate value if absent. */
    static long getOrInitializeConfiguredExpiration(Request request, long candidateExpirationNanos) {
        Long existing = request.attachments().putIfAbsent(CONFIGURED_EXPIRATION_NANOS, candidateExpirationNanos);
        return existing != null ? existing : candidateExpirationNanos;
    }

    @VisibleForTesting
    static @Nullable Long getConfiguredExpiration(Request request) {
        return request.attachments().getOrDefault(CONFIGURED_EXPIRATION_NANOS, null);
    }

    static void setDeadlineExpiration(Request request, long expirationNanos) {
        request.attachments().put(DEADLINE_EXPIRATION_NANOS, expirationNanos);
    }

    static void clearDeadlineExpiration(Request request) {
        request.attachments().remove(DEADLINE_EXPIRATION_NANOS);
    }

    static @Nullable Long getDeadlineExpiration(Request request) {
        return request.attachments().getOrDefault(DEADLINE_EXPIRATION_NANOS, null);
    }
}
