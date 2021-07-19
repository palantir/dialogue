/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.TestEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class QueueOverrideChannelTest {

    @Mock
    private Channel defaultDelegate;

    @Mock
    private Channel override;

    private Channel queueOverrideChannel;

    @BeforeEach
    public void beforeEach() {
        queueOverrideChannel = new QueueOverrideChannel(defaultDelegate);
    }

    @AfterEach
    public void afterEach() {
        verifyNoMoreInteractions(defaultDelegate, override);
    }

    @Test
    public void routesToDefault() {
        Request request = Request.builder().build();
        assertThat(queueOverrideChannel.execute(TestEndpoint.GET, request)).isNull();
        verify(defaultDelegate).execute(TestEndpoint.GET, request);
    }

    @Test
    public void routesToOverride() {
        Request request = Request.builder().build();
        QueueAttachments.setQueueOverride(request, override);
        assertThat(queueOverrideChannel.execute(TestEndpoint.GET, request)).isNull();
        verify(override).execute(TestEndpoint.GET, request);
    }
}
