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

import static org.mockito.Mockito.verify;

import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.UrlBuilder;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("FutureReturnValueIgnored")
public final class UserAgentChannelTest {

    private static final UserAgent baseAgent = UserAgent.of(UserAgent.Agent.of("test-class", "1.2.3"));
    private static final Endpoint endpoint = new Endpoint() {
        @Override
        public void renderPath(Map<String, String> params, UrlBuilder url) {}

        @Override
        public HttpMethod httpMethod() {
            return HttpMethod.GET;
        }

        @Override
        public String serviceName() {
            return "test-service";
        }

        @Override
        public String endpointName() {
            return "test-endpoint";
        }

        @Override
        public String version() {
            return "2.3.4";
        }
    };

    @Mock private Channel delegate;
    private UserAgentChannel channel;

    @Before
    public void before() {
        channel = new UserAgentChannel(delegate, baseAgent);
    }

    private Request request = Request.builder()
            .putHeaderParams("header", "value")
            .putQueryParams("query", "value")
            .putPathParams("path", "value")
            .build();

    @Test
    public void injectsDialogueVersionAndEndpointVersion() {
        // Special case: In IDEs, tests are run against classes (not JARs) and thus don't carry versions.
        final String dialogueVersion;
        if (System.getenv().get("CI") != null) {
            dialogueVersion = Channel.class.getPackage().getImplementationVersion();
        } else {
            dialogueVersion = "0.0.0";
        }

        Request augmentedRequest = Request.builder()
                .from(request)
                .putHeaderParams("user-agent", "test-class/1.2.3 test-service/2.3.4 dialogue/" + dialogueVersion)
                .build();
        channel.execute(endpoint, request);
        verify(delegate).execute(endpoint, augmentedRequest);
    }
}
