/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgents;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.SingleEndpointChannel;

/**
 * Adds a {@code user-agent} header that is the combination of the given base user agent, the version of the
 * dialogue library (extracted from this package's implementation version), and the name and version of the
 * {@link Endpoint}'s target service and endpoint.
 */
final class UserAgentChannel implements Channel2 {

    private static final UserAgent.Agent DIALOGUE_AGENT = extractDialogueAgent();

    private final Channel2 delegate;
    private final UserAgent baseAgent;

    UserAgentChannel(Channel2 delegate, UserAgent baseAgent) {
        this.delegate = delegate;
        this.baseAgent = baseAgent;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Request newRequest = Request.builder()
                .from(request)
                .putHeaderParams("user-agent", UserAgents.format(augmentUserAgent(baseAgent, endpoint)))
                .build();
        return delegate.execute(endpoint, newRequest);
    }

    private static UserAgent augmentUserAgent(UserAgent baseAgent, Endpoint endpoint) {
        return baseAgent
                .addAgent(UserAgent.Agent.of(endpoint.serviceName(), endpoint.version()))
                .addAgent(DIALOGUE_AGENT);
    }

    private static UserAgent.Agent extractDialogueAgent() {
        String maybeDialogueVersion = Channel.class.getPackage().getImplementationVersion();
        return UserAgent.Agent.of("dialogue", maybeDialogueVersion != null ? maybeDialogueVersion : "0.0.0");
    }

    @Override
    public String toString() {
        return "UserAgentChannel{baseAgent=" + UserAgents.format(baseAgent) + ", delegate=" + delegate + '}';
    }

    @Override
    public SingleEndpointChannel bindEndpoint(Endpoint endpoint) {
        return new UserAgentEndpointChannel(endpoint);
    }

    private class UserAgentEndpointChannel implements SingleEndpointChannel {
        private final String userAgent;
        private final SingleEndpointChannel proceed;

        private UserAgentEndpointChannel(Endpoint endpoint) {
            this.userAgent = UserAgents.format(augmentUserAgent(baseAgent, endpoint));
            this.proceed = delegate.bindEndpoint(endpoint);
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            Request newRequest = Request.builder()
                    .from(request)
                    .putHeaderParams("user-agent", userAgent)
                    .build();
            return proceed.execute(newRequest);
        }
    }
}
