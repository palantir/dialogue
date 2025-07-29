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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

// advertises to a server that this client may accept conjure error parameters serialized as json
final class AcceptJsonErrorDeserializationChannel implements Channel {
    private static final String HEADER_NAME = "Accept-Conjure-Error-Params-Type";
    private static final String ACCEPTED_TYPE = "JSON";

    private final Channel delegate;
    private final boolean enable;

    AcceptJsonErrorDeserializationChannel(Channel delegate, boolean enable) {
        this.delegate = delegate;
        this.enable = enable;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Request.Builder requestBuilder = Request.builder().from(request);
        if (enable) {
            requestBuilder.putHeaderParams(HEADER_NAME, ACCEPTED_TYPE);
        }
        return delegate.execute(endpoint, requestBuilder.build());
    }
}
