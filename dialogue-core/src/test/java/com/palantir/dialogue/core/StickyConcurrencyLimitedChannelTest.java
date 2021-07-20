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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class StickyConcurrencyLimitedChannelTest {

    @Mock
    private Endpoint endpoint;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private LimitedChannel delegate;

    @Mock
    private CautiousIncreaseAggressiveDecreaseConcurrencyLimiter mockLimiter;

    @Mock
    private CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit permit;

    private LimitedChannel channel;

    @BeforeEach
    public void before() {
        channel = new StickyConcurrencyLimitedChannel(delegate, mockLimiter, "test");
    }

    @Test
    public void first_in_flight_request_is_forced() {
        whenAcquireSuccessful();
        whenOnlyInFlight(true);
        whenDelegateCallSuccessfulIgnoreResult();

        assertThat(channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED))
                .isPresent();
        verify(delegate).maybeExecute(endpoint, request, LimitEnforcement.DANGEROUS_BYPASS_LIMITS);
    }

    @Test
    public void subsequent_requests_respect_limits() {
        whenAcquireSuccessful();
        whenOnlyInFlight(false);
        whenDelegateCallSuccessfulIgnoreResult();

        assertThat(channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED))
                .isPresent();
        verify(delegate).maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED);
    }

    @Test
    public void delegate_reject_drops() {
        whenAcquireSuccessful();
        whenOnlyInFlight(false);
        whenDelegateRejects();

        assertThat(channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED))
                .isEmpty();
        verify(delegate).maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED);
        verify(permit).dropped();
    }

    @Test
    public void delegate_future_releases_permit() {
        whenAcquireSuccessful();
        whenOnlyInFlight(false);
        SettableFuture<Response> responseSettableFuture = whenDelegateCallSuccessful();

        ListenableFuture<Response> responseListenableFuture = channel.maybeExecute(
                        endpoint, request, LimitEnforcement.DEFAULT_ENABLED)
                .get();

        responseSettableFuture.set(response);

        assertThat(responseListenableFuture)
                .isDone()
                .succeedsWithin(Duration.ZERO)
                .isEqualTo(response);
        verify(permit).onSuccess(response);
    }

    @Test
    public void limit_enforcement_passthrough() {
        whenAcquireSuccessful();
        whenOnlyInFlight(false);
        whenDelegateCallSuccessfulIgnoreResult();

        assertThat(channel.maybeExecute(endpoint, request, LimitEnforcement.DANGEROUS_BYPASS_LIMITS))
                .isPresent();
        verify(mockLimiter).acquire(LimitEnforcement.DANGEROUS_BYPASS_LIMITS);
        verify(delegate).maybeExecute(endpoint, request, LimitEnforcement.DANGEROUS_BYPASS_LIMITS);
    }

    @Test
    public void acquire_fails() {
        when(mockLimiter.acquire(any())).thenReturn(Optional.empty());
        assertThat(channel.maybeExecute(endpoint, request, LimitEnforcement.DANGEROUS_BYPASS_LIMITS))
                .isEmpty();
        verifyNoInteractions(permit, delegate);
    }

    private void whenAcquireSuccessful() {
        when(mockLimiter.acquire(any())).thenReturn(Optional.of(permit));
    }

    private void whenOnlyInFlight(boolean onlyInFlight) {
        when(permit.isOnlyInFlight()).thenReturn(onlyInFlight);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void whenDelegateCallSuccessfulIgnoreResult() {
        whenDelegateCallSuccessful();
    }

    private SettableFuture<Response> whenDelegateCallSuccessful() {
        SettableFuture<Response> responseSettableFuture = SettableFuture.create();
        when(delegate.maybeExecute(any(), any(), any())).thenReturn(Optional.of(responseSettableFuture));
        return responseSettableFuture;
    }

    private void whenDelegateRejects() {
        when(delegate.maybeExecute(any(), any(), any())).thenReturn(Optional.empty());
    }
}
