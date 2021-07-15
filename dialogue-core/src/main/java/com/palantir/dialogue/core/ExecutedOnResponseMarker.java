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
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import java.util.Optional;

final class ExecutedOnResponseMarker {

    private ExecutedOnResponseMarker() {}

    public static Optional<ListenableFuture<Response>> maybeMarkExecutedOn(
            Request request,
            Optional<ListenableFuture<Response>> responseFuture,
            HostAndLimitedChannel hostAndLimitedChannel) {
        if (responseFuture.isPresent() && RoutingAttachments.shouldAttachExecutedOnChannelResponseAttachment(request)) {
            return Optional.of(DialogueFutures.transform(
                    responseFuture.get(),
                    response -> addExecutedOnResponseAttachment(response, hostAndLimitedChannel)));
        } else {
            return responseFuture;
        }
    }

    private static Response addExecutedOnResponseAttachment(
            Response response, HostAndLimitedChannel hostAndLimitedChannel) {
        RoutingAttachments.setExecutedOnChannelResponseAttachment(response, hostAndLimitedChannel);
        return response;
    }
}
