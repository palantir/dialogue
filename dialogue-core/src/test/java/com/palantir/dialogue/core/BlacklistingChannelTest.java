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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BlacklistingChannelTest {

    private static final Duration BLACKLIST_DURATION = Duration.ofSeconds(10);

    @Mock private LimitedChannel delegate;
    @Mock private Ticker ticker;
    @Mock private Endpoint endpoint;
    @Mock private Request request;
    @Mock private Response successfulResponse;
    private BlacklistingChannel channel;
    private SettableFuture<Response> response;

    @Before
    public void before() {
        channel = new BlacklistingChannel(delegate, BLACKLIST_DURATION, ticker);

        response = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.of(response));
    }

    @Test
    public void testBlacklistAfterError() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(response);

        response.setException(new IllegalStateException());
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos() - 1);
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());
        assertThat(channel.maybeExecute(endpoint, request)).contains(response);
    }

    @Test
    public void testNotBlacklistedAfterSuccess() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(response);

        response.set(successfulResponse);

        assertThat(channel.maybeExecute(endpoint, request)).contains(response);
    }

    @Test
    public void testConcurrentRequestsAllowed() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(response);
        assertThat(channel.maybeExecute(endpoint, request)).contains(response);
    }
}
