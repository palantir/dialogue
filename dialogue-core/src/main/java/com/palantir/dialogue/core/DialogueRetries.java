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

import com.palantir.dialogue.Response;
import com.palantir.dialogue.RetriesExhaustedException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class DialogueRetries {
    private static final String DIALOGUE_RETRIES_EXHAUSTED_HEADER = "Dialogue-Retries-Exhausted";

    private static final int MAX_CAUSE_CHAIN_LENGTH = 100;

    private DialogueRetries() {}

    static boolean isRetriesExhausted(Response response) {
        Boolean result = response.attachments().getOrDefault(RetriesExhaustedException.RESPONSE_ATTACHMENT_KEY, false);
        return result != null && result;
    }

    static boolean isRetriesExhausted(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        @Nullable Throwable current = throwable;
        while (current != null && visited.size() < MAX_CAUSE_CHAIN_LENGTH && visited.add(current)) {
            if (hasRetriesExhaustedMarker(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static void setRetriesExhausted(Response response) {
        response.attachments().put(RetriesExhaustedException.RESPONSE_ATTACHMENT_KEY, true);
    }

    static void setRetriesExhausted(Throwable throwable) {
        if (!isRetriesExhausted(throwable)) {
            throwable.addSuppressed(RetriesExhaustedException.INSTANCE);
        }
    }

    private static boolean hasRetriesExhaustedMarker(Throwable throwable) {
        for (Throwable suppressed : throwable.getSuppressed()) {
            if (suppressed instanceof RetriesExhaustedException) {
                return true;
            }
        }
        return false;
    }

    // TODO(blaub): perhaps change `value` to a value type with more metadata instead of just boolean
    public static <T> void encodeToResponse(
            boolean value, T response, DialogueRetriesResponseEncodingAdapter<? super T> adapter) {
        if (value) {
            adapter.setHeader(response, DIALOGUE_RETRIES_EXHAUSTED_HEADER, "true");
        }
    }

    public static <T> boolean parseFromResponse(T response, DialogueRetriesResponseDecodingAdapter<? super T> adapter) {
        Optional<String> maybeRetriesExhaustedHeader =
                adapter.getFirstHeader(response, DIALOGUE_RETRIES_EXHAUSTED_HEADER);
        if (maybeRetriesExhaustedHeader.isEmpty()) {
            return false;
        } else {
            try {
                return "true".equalsIgnoreCase(maybeRetriesExhaustedHeader.get());
            } catch (Exception e) {
                return false;
            }
        }
    }

    public interface DialogueRetriesResponseEncodingAdapter<RESPONSE> {
        void setHeader(RESPONSE response, String headerName, String headerValue);
    }

    public interface DialogueRetriesResponseDecodingAdapter<RESPONSE> {
        Optional<String> getFirstHeader(RESPONSE response, String headerName);
    }
}
