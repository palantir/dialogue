/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import java.util.Optional;

public final class DialogueRetries {
    static final ResponseAttachmentKey<Boolean> RETRIES_EXHAUSTED_TOKEN = ResponseAttachmentKey.create(Boolean.class);

    private static final String DIALOGUE_RETRIES_EXHAUSTED_HEADER = "Dialogue-Retries-Exhausted";

    public static boolean isRetriesExhausted(Response response) {
        Boolean result = response.attachments().getOrDefault(RETRIES_EXHAUSTED_TOKEN, false);
        return result != null ? result : false;
    }

    public static void setRetriesExhausted(Response response) {
        response.attachments().put(RETRIES_EXHAUSTED_TOKEN, true);
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
