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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

final class InterruptionChannel implements EndpointChannel {
    private final EndpointChannel endpointChannel;

    InterruptionChannel(EndpointChannel endpointChannel) {
        this.endpointChannel = endpointChannel;
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        if (Thread.currentThread().isInterrupted()) {
            return Futures.immediateFailedFuture(new InterruptedException());
        }

        return endpointChannel.execute(request);
    }

    @Override
    public String toString() {
        return "DetectInterruptionChannel{" + endpointChannel + '}';
    }
}
