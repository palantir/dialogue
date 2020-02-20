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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.SettableFuture;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConcurrencyLimitedChannelTest {

    @Mock
    private Endpoint endpoint;

    @Mock
    private Request request;

    @Mock
    private Channel delegate;

    @Mock
    private SimpleLimiter<Void> limiter;

    @Mock
    private Limiter.Listener listener;

    @Mock
    private Response response;

    private ConcurrencyLimitedChannel channel;
    private SettableFuture<Response> responseFuture;

    @BeforeEach
    public void before() {
        channel = new ConcurrencyLimitedChannel(new LimitedChannelAdapter(delegate), () -> limiter);

        responseFuture = SettableFuture.create();
        lenient().when(delegate.execute(endpoint, request)).thenReturn(responseFuture);
    }

    @Test
    public void testLimiterAvailable_successfulRequest() {
        mockLimitAvailable();
        mockResponseCode(200);

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(listener).onSuccess();
    }

    @Test
    public void testLimiterAvailable_429isDropped() {
        mockLimitAvailable();
        mockResponseCode(429);

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(listener).onDropped();
    }

    @Test
    public void testLimiterAvailable_exceptionIsIgnored() {
        mockLimitAvailable();
        responseFuture.setException(new IllegalStateException());

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(listener).onIgnore();
    }

    @Test
    public void testUnavailable() {
        mockLimitUnavailable();

        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testWithDefaultLimiter() {
        channel = ConcurrencyLimitedChannel.create(new LimitedChannelAdapter(delegate));

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
    }

    private void mockResponseCode(int code) {
        when(response.code()).thenReturn(code);
        responseFuture.set(response);
    }

    private void mockLimitAvailable() {
        when(limiter.acquire(null)).thenReturn(Optional.of(listener));
    }

    private void mockLimitUnavailable() {
        when(limiter.acquire(null)).thenReturn(Optional.empty());
    }
}
