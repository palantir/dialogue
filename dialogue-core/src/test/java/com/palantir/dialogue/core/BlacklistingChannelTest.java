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

    @Mock
    private LimitedChannel delegate;

    @Mock
    private Ticker ticker;

    @Mock
    private Endpoint endpoint;

    @Mock
    private Request request;

    @Mock
    private Response response;

    private BlacklistingChannel channel;
    private SettableFuture<Response> futureResponse;

    @Before
    public void before() {
        channel = new BlacklistingChannel(delegate, BLACKLIST_DURATION, ticker);

        futureResponse = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.of(futureResponse));
    }

    @Test
    public void testBlacklistAfterError() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);

        futureResponse.setException(new IllegalStateException());
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
    }

    @Test
    public void testBlacklistAfter503() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);

        futureResponse.set(mockResponseWithCode(503));
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
    }

    @Test
    public void testBlacklistAfter500() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);

        futureResponse.set(mockResponseWithCode(500));
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
    }

    @Test
    public void testBlacklistedForDuration() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);

        futureResponse.setException(new IllegalStateException());
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos() - 1);
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);
    }

    @Test
    public void testNotBlacklistedAfterSuccess() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);

        futureResponse.set(mockResponseWithCode(200));

        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);
    }

    @Test
    public void after_blacklisting_expires_only_5_requests_are_allowed_to_start() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);
        futureResponse.set(mockResponseWithCode(503));

        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        SettableFuture<Response> unresolvedResponse = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.of(unresolvedResponse));
        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());

        assertThat(channel.maybeExecute(endpoint, request)).contains(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).contains(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).contains(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).contains(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).contains(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
    }

    @Test
    public void after_blacklisting_expires_and_5_requests_pass_we_are_good_to_go_again() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);
        futureResponse.set(mockResponseWithCode(503));

        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        SettableFuture<Response> thisWillSucceed = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.of(thisWillSucceed));
        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());

        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        thisWillSucceed.set(mockResponseWithCode(200));

        assertThat(channel.maybeExecute(endpoint, request)).isPresent();
    }

    @Test
    public void failure_during_probation_puts_us_back_into_blacklisting() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);
        futureResponse.set(mockResponseWithCode(503));

        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        SettableFuture<Response> thisWill503 = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(Optional.of(thisWill503));
        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());

        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).contains(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();

        thisWill503.set(mockResponseWithCode(503));

        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
    }

    @Test
    public void testConcurrentRequestsAllowed() {
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);
        assertThat(channel.maybeExecute(endpoint, request)).contains(futureResponse);
    }

    private Response mockResponseWithCode(int code) {
        when(response.code()).thenReturn(code);
        return response;
    }
}
