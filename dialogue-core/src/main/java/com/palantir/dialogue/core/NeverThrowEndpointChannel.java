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
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;

final class NeverThrowEndpointChannel implements EndpointChannel {
    private static final SafeLogger log = SafeLoggerFactory.get(NeverThrowEndpointChannel.class);
    private final EndpointChannel proceed;

    NeverThrowEndpointChannel(EndpointChannel proceed) {
        this.proceed = proceed;
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        try {
            return proceed.execute(request);
        } catch (RuntimeException | Error e) {
            log.error("Dialogue channels should never throw. This may be a bug in the channel implementation", e);
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public String toString() {
        return "NeverThrowEndpointChannel{" + proceed + '}';
    }
}
