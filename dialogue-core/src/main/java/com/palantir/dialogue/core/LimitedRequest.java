/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

/**
 * A request being considered by {@link LimitedChannel}.
 * By design this does not expose the {@link Request} or {@link Endpoint} values because it's invalid
 * for a {@link LimitedChannel} to consider them when limiting a request.
 */
interface LimitedRequest {

    ListenableFuture<Response> execute(Channel channel);

    /** Create a simple {@link LimitedRequest}. */
    static LimitedRequest of(Endpoint endpoint, Request request) {
        return new LimitedRequest() {
            @Override
            public ListenableFuture<Response> execute(Channel channel) {
                return channel.execute(endpoint, request);
            }

            @Override
            public String toString() {
                return "LimitedRequest{endpoint=" + endpoint + ", request=" + request + "}";
            }
        };
    }
}
