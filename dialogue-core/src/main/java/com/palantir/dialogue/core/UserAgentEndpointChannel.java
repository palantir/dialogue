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
import com.palantir.dialogue.BlockingEndpointChannel;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.io.IOException;

/**
 * Adds a {@code user-agent} header that is the combination of the given base user agent, the version of the
 * dialogue library (extracted from this package's implementation version), and the name and version of the
 * {@link Endpoint}'s target service and endpoint.
 */
final class UserAgentEndpointChannel implements EndpointFilter2 {
    static final UserAgent.Agent DIALOGUE_AGENT = extractDialogueAgent();

    private final String userAgent;

    private UserAgentEndpointChannel(String userAgent) {
        this.userAgent = userAgent;
    }

    static EndpointFilter2 create(Endpoint endpoint, UserAgent baseAgent) {
        String userAgent = UserAgents.format(augmentUserAgent(baseAgent, endpoint));
        return new UserAgentEndpointChannel(userAgent);
    }

    @Override
    public Response executeBlocking(Request request, BlockingEndpointChannel next) throws IOException {
        Request newRequest = augment(request);
        return next.execute(newRequest);
    }

    @Override
    public ListenableFuture<Response> executeAsync(Request request, EndpointChannel next) {
        Request newRequest = augment(request);
        return next.execute(newRequest);
    }

    private Request augment(Request request) {
        return Request.builder()
                .from(request)
                .putHeaderParams("user-agent", userAgent)
                .build();
    }

    private static UserAgent augmentUserAgent(UserAgent baseAgent, Endpoint endpoint) {
        String endpointVersion = endpoint.version();

        // Until conjure-java 5.14.2, we mistakenly embedded 0.0.0 in everything. This fallback logic attempts
        // to work-around this and produce a more helpful user agent
        if (endpointVersion.equals("0.0.0")) {
            String jarVersion = endpoint.getClass().getPackage().getImplementationVersion();
            if (jarVersion != null) {
                endpointVersion = jarVersion;
            }
        }

        return baseAgent
                .addAgent(UserAgent.Agent.of(endpoint.serviceName(), endpointVersion))
                .addAgent(DIALOGUE_AGENT);
    }

    private static UserAgent.Agent extractDialogueAgent() {
        String version = dialogueVersion();
        return UserAgent.Agent.of("dialogue", version);
    }

    static String dialogueVersion() {
        String maybeDialogueVersion = Channel.class.getPackage().getImplementationVersion();
        return maybeDialogueVersion != null ? maybeDialogueVersion : "0.0.0";
    }

    @Override
    public String toString() {
        return "UserAgentEndpointChannel{userAgent='" + userAgent + '}';
    }
}
