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

import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.blocking.BlockingChannel;
import java.io.IOException;

interface BlockingChannelFilter {

    Response executeFilter(Endpoint endpoint, Request request, BlockingChannel next) throws IOException;

    default BlockingChannel bind(BlockingChannel next) {
        return new BlockingChannel() {
            @Override
            public Response execute(Endpoint endpoint, Request request) throws IOException {
                return executeFilter(endpoint, request, next);
            }
        };
    }
}
