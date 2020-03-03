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

package com.palantir.conjure.java.dialogue.serde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class DefaultClientsTest {

    @Mock
    private Channel channel;

    @Mock
    private Endpoint endpoint;

    @Mock
    private Response response;

    @Mock
    private Deserializer<String> deserializer;

    @Test
    public void testAsync() throws ExecutionException, InterruptedException {
        Request request = Request.builder().build();
        when(deserializer.deserialize(eq(response))).thenReturn("value");
        SettableFuture<Response> responseFuture = SettableFuture.create();
        when(channel.execute(eq(endpoint), eq(request))).thenReturn(responseFuture);
        ListenableFuture<String> result = DefaultClients.INSTANCE.call(channel, endpoint, request, deserializer);
        assertThat(result).isNotDone();
        responseFuture.set(response);
        assertThat(result).isDone();
        assertThat(result.get()).isEqualTo("value");
    }

    @Test
    public void testBlocking() {
        Request request = Request.builder().build();
        when(deserializer.deserialize(eq(response))).thenReturn("value");
        when(channel.execute(eq(endpoint), eq(request))).thenReturn(Futures.immediateFuture(response));
        assertThat(DefaultClients.INSTANCE.blocking(channel, endpoint, request, deserializer))
                .isEqualTo("value");
    }
}
