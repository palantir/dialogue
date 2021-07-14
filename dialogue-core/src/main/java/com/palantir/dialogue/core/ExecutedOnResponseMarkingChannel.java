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
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import java.util.Optional;

final class ExecutedOnResponseMarkingChannel implements HostLimitedChannel {

    private final HostIdx hostIdx;
    private final LimitedChannel delegate;

    private ExecutedOnResponseMarkingChannel(HostIdx hostIdx, LimitedChannel delegate) {
        this.hostIdx = hostIdx;
        this.delegate = delegate;
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request request, LimitEnforcement limitEnforcement) {
        if (RoutingAttachments.shouldAttachExecutedOnResponseAttachment(request)) {
            return delegate.maybeExecute(endpoint, request, limitEnforcement)
                    .map(responseFuture ->
                            DialogueFutures.transform(responseFuture, this::addExecutedOnResponseAttachment));
        } else {
            return delegate.maybeExecute(endpoint, request, limitEnforcement);
        }
    }

    static HostLimitedChannel create(HostIdx hostIdx, LimitedChannel delegate) {
        return new ExecutedOnResponseMarkingChannel(hostIdx, delegate);
    }

    private Response addExecutedOnResponseAttachment(Response response) {
        RoutingAttachments.executedOn(response, this);
        return response;
    }

    @Override
    public HostIdx getHostIdx() {
        return hostIdx;
    }
}
