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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CautiousIncreaseAggressiveDecreaseConcurrencyLimiterTest {

    private CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter;

    @BeforeEach
    public void before() {
        limiter = new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(Behavior.HOST_LEVEL);
    }

    @Test
    void acquire_returnsPermitssWhileInflightPermitLimitNotReached() {
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

    @Test
    public void ignore_releasesPermit() {
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().ignore();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @Test
    public void ignore_doesNotChangeLimits() {
        double max = limiter.getLimit();
        limiter.acquire().get().ignore();
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void dropped_releasesPermit() {
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().dropped();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @Test
    public void dropped_reducesLimit() {
        double max = limiter.getLimit();
        limiter.acquire().get().dropped();
        assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
    }

    @Test
    public void success_releasesPermit() {
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().success();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @Test
    public void success_increasesLimitOnlyIfSufficientNumberOfRequestsAreInflight() {
        double max = limiter.getLimit();
        for (int i = 0; i < max * .9; ++i) {
            limiter.acquire().get();
            assertThat(limiter.getLimit()).isEqualTo(max);
        }

        limiter.acquire().get().success();
        assertThat(limiter.getLimit()).isGreaterThan(max).isLessThanOrEqualTo(max + 1);
    }

    @Test
    public void onSuccess_releasesSuccessfully() {
        Response response = mock(Response.class);
        when(response.code()).thenReturn(200);

        double max = limiter.getLimit();
        limiter.acquire().get().onSuccess(response);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onSuccess_dropsIfResponseIndicatesQosOrError() {
        for (int code : new int[] {308, 429, 503, 599}) {
            Response response = mock(Response.class);
            when(response.code()).thenReturn(code);

            double max = limiter.getLimit();
            limiter.acquire().get().onSuccess(response);
            assertThat(limiter.getLimit()).isCloseTo(max * 0.9, Percentage.withPercentage(5));
        }
    }

    @Test
    public void onFailure_dropsIfIoException() {
        IOException exception = new IOException();

        double max = limiter.getLimit();
        limiter.acquire().get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
    }

    @Test
    public void onFailure_ignoresForNonIoExceptions() {
        RuntimeException exception = new RuntimeException();

        double max = limiter.getLimit();
        limiter.acquire().get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }
}
