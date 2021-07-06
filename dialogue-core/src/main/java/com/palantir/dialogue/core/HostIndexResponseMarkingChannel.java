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
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingAttachments;
import com.palantir.dialogue.RoutingAttachments.HostId;
import com.palantir.dialogue.futures.DialogueFutures;

final class HostIndexResponseMarkingChannel implements Channel {

    private final HostId hostIndex;
    private final Channel delegate;

    private HostIndexResponseMarkingChannel(int hostIndex, Channel delegate) {
        this.hostIndex = HostId.of(hostIndex);
        this.delegate = delegate;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Boolean attachHostId = request.attachments().getOrDefault(RoutingAttachments.ATTACH_HOST_ID, Boolean.FALSE);
        if (Boolean.TRUE.equals(attachHostId)) {
            return DialogueFutures.transform(delegate.execute(endpoint, request), this::addHostId);
        } else {
            return delegate.execute(endpoint, request);
        }
    }

    static Channel create(int hostIndex, Channel delegate) {
        return new HostIndexResponseMarkingChannel(hostIndex, delegate);
    }

    private Response addHostId(Response response) {
        response.attachments().put(RoutingAttachments.EXECUTED_ON_HOST_ID_RESPONSE_ATTACHMENT_KEY, hostIndex);
        return response;
    }
}
