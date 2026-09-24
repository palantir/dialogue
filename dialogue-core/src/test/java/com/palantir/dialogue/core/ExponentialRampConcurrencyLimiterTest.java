/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ExponentialRampConcurrencyLimiterTest {

    private final ExponentialRampConcurrencyLimiter limiter =
            new ExponentialRampConcurrencyLimiter(Behavior.HOST_LEVEL);

    @ParameterizedTest
    @ValueSource(doubles = {-1, 0, 0.5, 1_000_001, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void rejectsInvalidInitialLimit(double initialLimit) {
        assertThatThrownBy(() -> new ExponentialRampConcurrencyLimiter(Behavior.HOST_LEVEL, initialLimit))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessageContaining("initialLimit must be within the supported range");
    }

    @Test
    void increasesLimitByOne() {
        // 18 inflight requests will satisfy the 90% saturation threshold.
        List<ConcurrencyLimiter.Permit> permits = acquire(18);
        permits.get(permits.size() - 1).onSuccess(new TestResponse().code(200));
        assertThat(limiter.getLimit()).isEqualTo(21D);
    }

    @Test
    void firstDropPermanentlySwitchesToAimd() {
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).onFailure(new IOException("failure"));
        assertThat(limiter.isInExponentialRamp()).isFalse();
        assertThat(limiter.getLimit()).isEqualTo(18D);
        List<ConcurrencyLimiter.Permit> permits = acquire(16);
        permits.get(permits.size() - 1).onSuccess(new TestResponse().code(200));
        assertThat(limiter.getLimit()).isEqualTo(18D + 1D / 18D);
        assertThat(limiter.isInExponentialRamp()).isFalse();
    }

    private List<ConcurrencyLimiter.Permit> acquire(int count) {
        List<ConcurrencyLimiter.Permit> permits = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ConcurrencyLimiter.Permit permit = limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
            assertThat(permit).isNotNull();
            permits.add(permit);
        }
        return permits;
    }
}
