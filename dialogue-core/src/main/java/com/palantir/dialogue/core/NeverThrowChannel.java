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
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ChannelEndpointStage;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The contract of {@link Channel} requires that the {@link Channel#execute} method never throws. This is a defensive
 * backstop so that callers can rely on this invariant.
 */
final class NeverThrowChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(NeverThrowChannel.class);
    private final Channel delegate;

    NeverThrowChannel(Channel delegate) {
        this.delegate = delegate;
    }

    static ChannelEndpointStage create(ChannelEndpointStage delegate) {
        return endpoint -> {
            EndpointChannel proceed = delegate.endpoint(endpoint);
            return new NeverThrowEndpointChannel(proceed);
        };
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        try {
            return delegate.execute(endpoint, request);
        } catch (RuntimeException | Error e) {
            log.error("Dialogue channels should never throw. This may be a bug in the channel implementation", e);
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public String toString() {
        return "NeverThrowChannel{" + delegate + '}';
    }

    private static final class NeverThrowEndpointChannel implements EndpointChannel {
        private final EndpointChannel proceed;

        private NeverThrowEndpointChannel(EndpointChannel proceed) {
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
    }
}
