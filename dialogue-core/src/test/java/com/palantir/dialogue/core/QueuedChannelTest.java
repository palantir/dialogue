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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;

@RunWith(MockitoJUnitRunner.class)
public class QueuedChannelTest {

    @Mock private LimitedChannel delegate;
    @Mock private Endpoint endpoint;
    @Mock private Request request;
    @Mock private Response mockResponse;
    private QueuedChannel queuedChannel;
    private SettableFuture<Response> futureResponse;
    private Optional<ListenableFuture<Response>> maybeResponse;

    @Before
    public void before() {
        queuedChannel = new QueuedChannel(delegate);
        futureResponse = SettableFuture.create();
        maybeResponse = Optional.of(futureResponse);

        mockHasCapacity();
    }

    @Test
    public void testReceivesSuccessfulResponse() throws ExecutionException, InterruptedException {
        ListenableFuture<Response> response = queuedChannel.execute(endpoint, request);
        assertThat(response.isDone()).isFalse();

        futureResponse.set(mockResponse);

        assertThat(response.isDone()).isTrue();
        assertThat(response.get()).isEqualTo(mockResponse);
    }

    @Test
    public void testReceivesExceptionalResponse() {
        ListenableFuture<Response> response = queuedChannel.execute(endpoint, request);
        assertThat(response.isDone()).isFalse();

        futureResponse.setException(new IllegalArgumentException());

        assertThat(response.isDone()).isTrue();
        assertThatThrownBy(() -> response.get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testQueuedRequestExecutedOnNextSubmission() {
        mockNoCapacity();
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(1)).maybeExecute(endpoint, request);

        mockHasCapacity();
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(3)).maybeExecute(endpoint, request);
    }

    @Test
    public void testQueuedRequestExecutedWhenRunningRequestCompletes() {
        mockHasCapacity();
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(1)).maybeExecute(endpoint, request);

        mockNoCapacity();
        queuedChannel.execute(endpoint, request);
        verify(delegate, times(2)).maybeExecute(endpoint, request);

        futureResponse.set(mockResponse);

        verify(delegate, times(3)).maybeExecute(endpoint, request);
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockHasCapacity() {
        return when(delegate.maybeExecute(endpoint, request)).thenReturn(maybeResponse);
    }

    private OngoingStubbing<Optional<ListenableFuture<Response>>> mockNoCapacity() {
        return when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.empty());
    }
}
