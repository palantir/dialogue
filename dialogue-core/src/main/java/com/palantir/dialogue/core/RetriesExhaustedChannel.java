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

package com.palantir.dialogue.core;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.DialogueRetries;
import com.palantir.dialogue.DialogueRetries.DialogueRetriesResponseDecodingAdapter;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import java.util.Optional;

// a channel that detects if upstream dialogue retries were exhausted, and fails fast if so
final class RetriesExhaustedChannel implements EndpointChannel {
    private final EndpointChannel delegate;

    RetriesExhaustedChannel(EndpointChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        return DialogueFutures.transformAsync(delegate.execute(request), response -> {
            boolean retriesExhausted =
                    DialogueRetries.parseFromResponse(response, RetriesExhaustedResponseDecodingAdapter.INSTANCE);
            if (retriesExhausted) {
                // error-deserializing code and RetryingChannel looks for this
                DialogueRetries.setRetriesExhausted(response);
            }
            return Futures.immediateFuture(response);
        });
    }

    private enum RetriesExhaustedResponseDecodingAdapter implements DialogueRetriesResponseDecodingAdapter<Response> {
        INSTANCE;

        @Override
        public Optional<String> getFirstHeader(Response response, String headerName) {
            return response.getFirstHeader(headerName);
        }
    }
}
