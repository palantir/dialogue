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
import javax.annotation.Nullable;

final class RoutingAttachments {

    /** When present, {@link #EXECUTED_ON_CHANNEL} will be set. */
    private static final RequestAttachmentKey<Boolean> ADD_EXECUTED_ON_CHANNEL_RESPONSE_ATTACHMENT =
            RequestAttachmentKey.create(Boolean.class);

    /**
     * This request attachment specifies that a request should be executed on a specific host channel.
     */
    private static final RequestAttachmentKey<HostAndLimitedChannel> EXECUTE_ON_CHANNEL =
            RequestAttachmentKey.create(HostAndLimitedChannel.class);

    /**
     * If {@link #ADD_EXECUTED_ON_CHANNEL_RESPONSE_ATTACHMENT} is requested, this attachment will be present on the response
     * to indicate the host channel that executed the request.
     */
    private static final ResponseAttachmentKey<HostAndLimitedChannel> EXECUTED_ON_CHANNEL =
            ResponseAttachmentKey.create(HostAndLimitedChannel.class);

    private RoutingAttachments() {}

    static boolean shouldAttachExecutedOnChannelResponseAttachment(Request request) {
        return Boolean.TRUE.equals(
                request.attachments().getOrDefault(ADD_EXECUTED_ON_CHANNEL_RESPONSE_ATTACHMENT, Boolean.FALSE));
    }

    static void requestExecutedOnChannelResponseAttachment(Request request) {
        request.attachments().put(ADD_EXECUTED_ON_CHANNEL_RESPONSE_ATTACHMENT, Boolean.TRUE);
    }

    static void setExecutedOnChannelResponseAttachment(Response response, HostAndLimitedChannel limitedChannel) {
        response.attachments().put(EXECUTED_ON_CHANNEL, limitedChannel);
    }

    @Nullable
    static HostAndLimitedChannel maybeGetExecuteOnChannel(Request request) {
        return request.attachments().getOrDefault(EXECUTE_ON_CHANNEL, null);
    }

    static void setExecuteOnChannel(Request request, HostAndLimitedChannel hostAndLimitedChannel) {
        request.attachments().put(EXECUTE_ON_CHANNEL, hostAndLimitedChannel);
    }

    static HostAndLimitedChannel getExecutedOnChannel(Response response) {
        return Preconditions.checkNotNull(
                response.attachments().getOrDefault(EXECUTED_ON_CHANNEL, null), "attachment not present");
    }
}
