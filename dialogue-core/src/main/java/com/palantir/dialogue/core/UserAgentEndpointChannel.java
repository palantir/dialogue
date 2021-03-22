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
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds a {@code user-agent} header that is the combination of the given base user agent, the version of the
 * dialogue library (extracted from this package's implementation version), and the name and version of the
 * {@link Endpoint}'s target service and endpoint.
 */
final class UserAgentEndpointChannel implements EndpointChannel {
    private static final Logger log = LoggerFactory.getLogger(UserAgentEndpointChannel.class);
    static final UserAgent.Agent DIALOGUE_AGENT = extractDialogueAgent();

    private final EndpointChannel delegate;
    private final String userAgent;

    private UserAgentEndpointChannel(EndpointChannel delegate, String userAgent) {
        this.delegate = delegate;
        this.userAgent = userAgent;
    }

    static EndpointChannel create(EndpointChannel delegate, Endpoint endpoint, UserAgent baseAgent) {
        String userAgent = UserAgents.format(augmentUserAgent(baseAgent, endpoint));
        return new UserAgentEndpointChannel(delegate, userAgent);
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        Request newRequest = Request.builder()
                .from(request)
                .putHeaderParams("user-agent", userAgent)
                .build();
        return delegate.execute(newRequest);
    }

    private static UserAgent augmentUserAgent(UserAgent baseAgent, Endpoint endpoint) {
        return tryAddEndpointAgent(baseAgent, endpoint).addAgent(DIALOGUE_AGENT);
    }

    private static UserAgent tryAddEndpointAgent(UserAgent baseAgent, Endpoint endpoint) {
        String endpointService = endpoint.serviceName();
        String endpointVersion = getEndpointVersion(endpoint);
        try {
            return baseAgent.addAgent(UserAgent.Agent.of(endpoint.serviceName(), endpointVersion));
        } catch (IllegalArgumentException e) {
            log.debug(
                    "Failed to construct UserAgent for service {} version {}. "
                            + "This information will not be included",
                    SafeArg.of("service", endpointService),
                    SafeArg.of("version", endpointVersion),
                    e);
            return baseAgent;
        }
    }

    private static String getEndpointVersion(Endpoint endpoint) {
        String endpointVersion = endpoint.version();
        // Until conjure-java 5.14.2, we mistakenly embedded 0.0.0 in everything. This fallback logic attempts
        // to work-around this and produce a more helpful user agent
        if ("0.0.0".equals(endpointVersion)) {
            String jarVersion = endpoint.getClass().getPackage().getImplementationVersion();
            if (jarVersion != null) {
                return jarVersion;
            }
        }
        return endpointVersion;
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
        return "UserAgentEndpointChannel{userAgent='" + userAgent + "', proceed=" + delegate + '}';
    }
}
