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

import static com.palantir.dialogue.core.LimitedResponseUtils.assertThatIsClientLimited;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
    private SettableFuture<LimitedResponse> futureResponse;

    @BeforeEach
    public void before() {
        channel = new BlacklistingChannel(delegate, BLACKLIST_DURATION, () -> {}, ticker);

        futureResponse = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(futureResponse);
    }

    @Test
    public void testBlacklistAfterError() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);

        futureResponse.setException(new IllegalStateException());
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));
    }

    @Test
    public void testBlacklistAfterServerLimit() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);

        futureResponse.set(LimitedResponses.serverLimited(response));
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));
    }

    @Test
    public void testBlacklistAfterServerError() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);

        futureResponse.set(LimitedResponses.serverError(response));
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));
    }

    @Test
    public void testBlacklistedForDuration() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);

        futureResponse.setException(new IllegalStateException());
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));

        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos() - 1);
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));

        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);
    }

    @Test
    public void testNotBlacklistedAfterSuccess() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);

        futureResponse.set(LimitedResponses.success(response));

        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);
    }

    @Test
    public void after_blacklisting_expires_only_5_requests_are_allowed_to_start() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);
        futureResponse.set(LimitedResponses.serverLimited(response));

        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));

        SettableFuture<LimitedResponse> unresolvedResponse = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(unresolvedResponse);
        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());

        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(unresolvedResponse);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(unresolvedResponse);
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));
    }

    @Test
    public void after_blacklisting_expires_and_5_requests_pass_we_are_good_to_go_again() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);
        futureResponse.set(LimitedResponses.serverLimited(response));

        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));

        SettableFuture<LimitedResponse> thisWillSucceed = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(thisWillSucceed);
        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());

        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWillSucceed);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWillSucceed);
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));

        thisWillSucceed.set(LimitedResponses.success(response));

        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWillSucceed);
    }

    @Test
    public void failure_during_probation_puts_us_back_into_blacklisting() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);
        futureResponse.set(LimitedResponses.serverLimited(response));

        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));

        SettableFuture<LimitedResponse> thisWill503 = SettableFuture.create();
        when(delegate.maybeExecute(endpoint, request)).thenReturn(thisWill503);
        when(ticker.read()).thenReturn(BLACKLIST_DURATION.toNanos());

        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWill503);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(thisWill503);
        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));

        thisWill503.set(LimitedResponses.serverLimited(response));

        assertThatIsClientLimited(channel.maybeExecute(endpoint, request));
    }

    @Test
    public void testConcurrentRequestsAllowed() {
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);
        assertThat(channel.maybeExecute(endpoint, request)).isEqualTo(futureResponse);
    }
}
