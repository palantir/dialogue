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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Behavior;
import java.io.IOException;
import java.util.Optional;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class CautiousIncreaseAggressiveDecreaseConcurrencyLimiterTest {

    private static CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter(Behavior behavior) {
        return new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(behavior);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    void acquire_returnsPermitssWhileInflightPermitLimitNotReached(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        double max = limiter.getLimit();
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> latestPermit = null;
        for (int i = 0; i < max; ++i) {
            latestPermit = limiter.acquire();
            assertThat(latestPermit).isPresent();
        }

        // Limit reached, cannot acquire permit
        assertThat(limiter.getInflight()).isEqualTo((int) max);
        assertThat(limiter.acquire()).isEmpty();

        // Release one permit, can acquire new permit.
        latestPermit.get().ignore();
        assertThat(limiter.acquire()).isPresent();
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void ignore_releasesPermit(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().ignore();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void ignore_doesNotChangeLimits(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        double max = limiter.getLimit();
        limiter.acquire().get().ignore();
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void dropped_releasesPermit(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().dropped();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void dropped_reducesLimit(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        double max = limiter.getLimit();
        limiter.acquire().get().dropped();
        assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void success_releasesPermit(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().success();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void success_increasesLimitOnlyIfSufficientNumberOfRequestsAreInflight(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        double max = limiter.getLimit();
        for (int i = 0; i < max * .9; ++i) {
            limiter.acquire().get();
            assertThat(limiter.getLimit()).isEqualTo(max);
        }

        limiter.acquire().get().success();
        assertThat(limiter.getLimit()).isGreaterThan(max).isLessThanOrEqualTo(max + 1);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void onSuccess_releasesSuccessfully(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        Response response = mock(Response.class);
        when(response.code()).thenReturn(200);

        double max = limiter.getLimit();
        limiter.acquire().get().onSuccess(response);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onSuccess_dropsIfResponseIndicatesQosOrError_host() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.HOST_LEVEL);
        for (int code : new int[] {308, 503}) {
            Response response = mock(Response.class);
            when(response.code()).thenReturn(code);

            double max = limiter.getLimit();
            limiter.acquire().get().onSuccess(response);
            assertThat(limiter.getLimit()).as("For status %d", code).isCloseTo(max * 0.9, Percentage.withPercentage(5));
        }
    }

    @Test
    public void onSuccess_dropsIfResponseIndicatesQosOrError_endpoint() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.ENDPOINT_LEVEL);
        for (int code : new int[] {429, 599}) {
            Response response = mock(Response.class);
            when(response.code()).thenReturn(code);

            double max = limiter.getLimit();
            limiter.acquire().get().onSuccess(response);
            assertThat(limiter.getLimit()).as("For status %d", code).isCloseTo(max * 0.9, Percentage.withPercentage(5));
        }
    }

    @Test
    public void onFailure_dropsIfIoException_host() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.HOST_LEVEL);
        IOException exception = new IOException();

        double max = limiter.getLimit();
        limiter.acquire().get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
    }

    @Test
    public void onFailure_ignoresIfIoException_endpoint() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.ENDPOINT_LEVEL);
        IOException exception = new IOException();

        double max = limiter.getLimit();
        limiter.acquire().get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void onFailure_ignoresForNonIoExceptions(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        RuntimeException exception = new RuntimeException();

        double max = limiter.getLimit();
        limiter.acquire().get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }
}
