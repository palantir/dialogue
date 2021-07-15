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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestAttachmentKey;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.ResponseAttachmentKey;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.dialogue.futures.DialogueFutures;
import java.util.Optional;
import javax.annotation.CheckReturnValue;

public final class StickyAttachments {

    /**
     * Added to {@link com.palantir.dialogue.RequestAttachments} to opt into a {@link #STICKY_TOKEN} on the response.
     */
    public static final RequestAttachmentKey<Boolean> REQUEST_STICKY_TOKEN = RequestAttachmentKey.create(Boolean.class);

    /**
     * Maybe transferred from {@link com.palantir.dialogue.RequestAttachments} to
     * {@link com.palantir.dialogue.ResponseAttachments} as {@link #STICKY} to stick to the same channel.
     */
    public static final ResponseAttachmentKey<StickyTarget> STICKY_TOKEN =
            ResponseAttachmentKey.create(StickyTarget.class);

    /**
     * Used to execute requests against the same host.
     */
    public static final RequestAttachmentKey<StickyTarget> STICKY = RequestAttachmentKey.create(StickyTarget.class);

    private StickyAttachments() {}

    // package private, used exclusively
    interface StickyTarget {
        Optional<ListenableFuture<Response>> maybeExecute(
                Endpoint endpoint, Request request, LimitEnforcement limitEnforcement);
    }

    @CheckReturnValue
    static Optional<ListenableFuture<Response>> maybeExecute(
            LimitedChannel channel, Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        if (request != null
                && Boolean.TRUE.equals(request.attachments().getOrDefault(REQUEST_STICKY_TOKEN, Boolean.FALSE))) {
            return channel.maybeExecute(endpoint, request, limitEnforcement)
                    .map(future -> DialogueFutures.transform(future, response -> {
                        response.attachments().put(STICKY_TOKEN, channel::maybeExecute);
                        return response;
                    }));
        } else {
            return channel.maybeExecute(endpoint, request, limitEnforcement);
        }
    }
}
