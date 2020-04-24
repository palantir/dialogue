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
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AimdConcurrencyLimiterTest {

    private AimdConcurrencyLimiter limiter;

    @BeforeEach
    public void before() {
        limiter = new AimdConcurrencyLimiter();
    }

    @Test
    void acquire_returnsPermitssWhileInflightPermitLimitNotReached() {
        int max = limiter.getLimit();
        Optional<AimdConcurrencyLimiter.Permit> latestPermit = null;
        for (int i = 0; i < max; ++i) {
            latestPermit = limiter.acquire();
            assertThat(latestPermit).isPresent();
        }

        // Limit reached, cannot acquire permit
        assertThat(limiter.getInflight()).isEqualTo(max);
        assertThat(limiter.acquire()).isEmpty();

        // Release one permit, can acquire new permit.
        latestPermit.get().ignore();
        assertThat(limiter.acquire()).isPresent();
    }

    @Test
    public void ignore_releasesPermit() {
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<AimdConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().ignore();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @Test
    public void ignore_doesNotChangeLimits() {
        int max = limiter.getLimit();
        limiter.acquire().get().ignore();
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void dropped_releasesPermit() {
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<AimdConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().dropped();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @Test
    public void dropped_reducesLimit() {
        int max = limiter.getLimit();
        limiter.acquire().get().dropped();
        assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
    }

    @Test
    public void success_releasesPermit() {
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<AimdConcurrencyLimiter.Permit> permit = limiter.acquire();
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().success();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @Test
    public void success_increasesLimitOnlyIfSufficientNumberOfRequestsAreInflight() {
        int max = limiter.getLimit();
        for (int i = 0; i < max / 2; ++i) {
            limiter.acquire().get();
            assertThat(limiter.getLimit()).isEqualTo(max);
        }

        limiter.acquire().get().success();
        assertThat(limiter.getLimit()).isEqualTo(max + 1);
    }

    @Test
    public void onSuccess_releasesSuccessfully() {
        Response response = mock(Response.class);
        when(response.code()).thenReturn(200);

        int max = limiter.getLimit();
        limiter.acquire().get().onSuccess(response);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onSuccess_dropsIfResponseIndicatesQosOrError() {
        for (int code : new int[] {308, 429, 503, 599}) {
            Response response = mock(Response.class);
            when(response.code()).thenReturn(code);

            int max = limiter.getLimit();
            limiter.acquire().get().onSuccess(response);
            assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
        }
    }

    @Test
    public void onFailure_dropsIfIoException() {
        IOException exception = new IOException();

        int max = limiter.getLimit();
        limiter.acquire().get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
    }

    @Test
    public void onFailure_ignoresForNonIoExceptions() {
        RuntimeException exception = new RuntimeException();

        int max = limiter.getLimit();
        limiter.acquire().get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }
}
