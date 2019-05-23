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
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ChannelsTest {

    @Mock private Channel delegate;
    @Mock private Endpoint endpoint;
    @Mock private Request request;
    @Mock private Response response;
    private Channel channel;

    @Before
    public void before() {
        channel = Channels.create(ImmutableList.of(delegate));
    }

    @Test
    public void testRequestMakesItThrough() throws ExecutionException, InterruptedException {
        ListenableFuture<Response> expectedResponse = Futures.immediateFuture(response);
        when(delegate.execute(endpoint, request)).thenReturn(expectedResponse);

        assertThat(channel.execute(endpoint, request).get()).isEqualTo(response);
    }
}
