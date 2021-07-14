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
import com.palantir.dialogue.Response;
import com.palantir.dialogue.ResponseAttachmentKey;
import com.palantir.logsafe.Preconditions;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public final class RoutingAttachments {

    /** When present, {@link #EXECUTED_ON_CHANNEL} will be set. */
    private static final RequestAttachmentKey<Boolean> ADD_EXECUTED_ON_RESPONSE_ATTACHMENT =
            RequestAttachmentKey.create(Boolean.class);

    /**
     * This request attachment specifies that a request should be executed on a specific host channel.
     */
    private static final RequestAttachmentKey<HostLimitedChannel> EXECUTE_ON_CHANNEL =
            RequestAttachmentKey.create(HostLimitedChannel.class);

    /**
     * If {@link #ADD_EXECUTED_ON_RESPONSE_ATTACHMENT} is requested, this attachment will be present on the response
     * to indicate the host channel that executed the request.
     */
    private static final ResponseAttachmentKey<HostLimitedChannel> EXECUTED_ON_CHANNEL =
            ResponseAttachmentKey.create(HostLimitedChannel.class);

    private RoutingAttachments() {}

    static void requestExecutedOnResponseAttachment(Request request) {
        request.attachments().put(ADD_EXECUTED_ON_RESPONSE_ATTACHMENT, Boolean.TRUE);
    }

    static Consumer<Request> stickyRoute(Response initialResponse) {
        HostLimitedChannel limitedChannel = Preconditions.checkNotNull(
                initialResponse.attachments().getOrDefault(EXECUTED_ON_CHANNEL, null), "executedOnChannel");
        return request -> request.attachments().put(EXECUTE_ON_CHANNEL, limitedChannel);
    }

    static boolean shouldAttachExecutedOnResponseAttachment(Request request) {
        return Boolean.TRUE.equals(
                request.attachments().getOrDefault(ADD_EXECUTED_ON_RESPONSE_ATTACHMENT, Boolean.FALSE));
    }

    static void executedOn(Response response, HostLimitedChannel limitedChannel) {
        response.attachments().put(EXECUTED_ON_CHANNEL, limitedChannel);
    }

    @Nullable
    static HostLimitedChannel maybeGetExecuteOn(Request request) {
        return request.attachments().getOrDefault(EXECUTE_ON_CHANNEL, null);
    }

    static void setExecuteOn(Request request, HostLimitedChannel executeOn) {
        request.attachments().put(EXECUTE_ON_CHANNEL, executeOn);
    }
}
