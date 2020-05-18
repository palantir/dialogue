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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.TestEndpoint;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("FutureReturnValueIgnored")
public final class UserAgentChannelTest {

    private static final UserAgent baseAgent = UserAgent.of(UserAgent.Agent.of("test-class", "1.2.3"));

    @Mock
    private Channel2 delegate;

    @Captor
    private ArgumentCaptor<Request> requestCaptor;

    private UserAgentChannel channel;

    @BeforeEach
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
        String dialogueVersion = Optional.ofNullable(Channel.class.getPackage().getImplementationVersion())
                .orElse("0.0.0");

        channel.bindEndpoint(TestEndpoint.POST).execute(request);
        verify(delegate).execute(eq((Endpoint) TestEndpoint.POST), requestCaptor.capture());
        assertThat(requestCaptor.getValue().headerParams().get("user-agent"))
                .containsExactly("test-class/1.2.3 service/1.0.0 dialogue/" + dialogueVersion);
    }
}
