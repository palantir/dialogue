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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Behavior;
import com.palantir.dialogue.core.CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
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
    void acquire_returnsPermitsWhileInflightPermitLimitNotReached(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        double max = limiter.getLimit();
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> latestPermit = null;
        for (int i = 0; i < max; ++i) {
            latestPermit = limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
            assertThat(latestPermit).isPresent();
        }

        // Limit reached, cannot acquire permit
        assertThat(limiter.getInflight()).isEqualTo((int) max);
        assertThat(limiter.acquire(LimitEnforcement.DEFAULT_ENABLED)).isEmpty();

        // Release one permit, can acquire new permit.
        latestPermit.get().ignore();
        assertThat(limiter.acquire(LimitEnforcement.DEFAULT_ENABLED)).isPresent();
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    void acquire_doesNotAcquirePartialPermits(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);

        double max = limiter.getLimit();
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> latestPermit = null;
        for (int i = 0; i < max; ++i) {
            latestPermit = limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
            assertThat(latestPermit).isPresent();
        }

        latestPermit.get().success();
        assertThat(limiter.getLimit()).isEqualTo(behavior.initialLimit() + 1D / behavior.initialLimit());

        // Now we can only acquire one extra permit, not 2
        assertThat(limiter.acquire(LimitEnforcement.DEFAULT_ENABLED)).isPresent();
        assertThat(limiter.acquire(LimitEnforcement.DEFAULT_ENABLED)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void acquire_canTurnOffLimits(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);

        double max = limiter.getLimit();
        for (int i = 0; i < max; ++i) {
            assertThat(limiter.acquire(LimitEnforcement.DEFAULT_ENABLED)).isPresent();
        }

        assertThat(limiter.acquire(LimitEnforcement.DEFAULT_ENABLED)).isEmpty();
        assertThat(limiter.acquire(LimitEnforcement.DANGEROUS_BYPASS_LIMITS)).isPresent();
        assertThat(limiter.getInflight()).isEqualTo((int) (max + 1));
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void ignore_releasesPermit(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit =
                limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().ignore();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void ignore_doesNotChangeLimits(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().ignore();
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void dropped_releasesPermit(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit =
                limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
        assertThat(limiter.getInflight()).isEqualTo(1);
        permit.get().dropped();
        assertThat(limiter.getInflight()).isEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void dropped_reducesLimit(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().dropped();
        assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void success_releasesPermit(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        assertThat(limiter.getInflight()).isEqualTo(0);
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> permit =
                limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
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
            assertThat(limiter.acquire(LimitEnforcement.DEFAULT_ENABLED)).isPresent();
            assertThat(limiter.getLimit()).isEqualTo(max);
        }

        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().success();
        assertThat(limiter.getLimit()).isGreaterThan(max).isLessThanOrEqualTo(max + 1);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void onSuccess_releasesSuccessfully(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        Response response = mock(Response.class);
        when(response.code()).thenReturn(200);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onSuccess_dropsIfResponseIndicatesQosOrError_host_308() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.HOST_LEVEL);
        Response response = mock(Response.class);
        when(response.getFirstHeader(eq("Location"))).thenReturn(Optional.of("https://localhost"));
        when(response.code()).thenReturn(308);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).as("For status %d", 308).isCloseTo(max * 0.9, Percentage.withPercentage(5));
    }

    @Test
    public void onSuccess_successIfResponseIndicatesNonQos308() {
        // This represents google chunked-upload APIs which respond '308 Resume Incomplete' to indicate
        // a successful chunk upload request.
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.HOST_LEVEL);
        Response response = mock(Response.class);
        when(response.code()).thenReturn(308);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onSuccess_dropsIfResponseIndicatesQosOrError_host_503() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.HOST_LEVEL);
        Response response = mock(Response.class);
        when(response.code()).thenReturn(503);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).as("For status %d", 503).isCloseTo(max * 0.9, Percentage.withPercentage(5));
    }

    @Test
    public void onSuccess_dropsIfResponseIndicatesQosOrError_endpoint() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.ENDPOINT_LEVEL);
        int code = 429;
        Response response = mock(Response.class);
        when(response.code()).thenReturn(code);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).as("For status %d", code).isCloseTo(max * 0.9, Percentage.withPercentage(5));
    }

    @Test
    public void onSuccess_releasesSuccessfullyIfResponseIndicatesQosOrError_sticky() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.STICKY);
        int code = 429;
        Response response = mock(Response.class);
        when(response.code()).thenReturn(code);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onSuccess_ignoresIfResponseIndicatesUnknownServerError_endpoint() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.ENDPOINT_LEVEL);
        int code = 599;
        Response response = mock(Response.class);
        when(response.code()).thenReturn(code);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onSuccess_ignoresIfResponseIndicatesUnknownServerError_sticky() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.STICKY);
        int code = 599;
        Response response = mock(Response.class);
        when(response.code()).thenReturn(code);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onSuccess_dropsIfResponseIndicatesUnknownServerError_host() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.HOST_LEVEL);
        int code = 599;
        Response response = mock(Response.class);
        when(response.code()).thenReturn(code);

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onSuccess(response);
        assertThat(limiter.getLimit()).as("For status %d", code).isCloseTo(max * 0.9, Percentage.withPercentage(5));
    }

    @Test
    public void onFailure_dropsIfIoException_host() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.HOST_LEVEL);
        IOException exception = new IOException();

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo((int) (max * 0.9));
    }

    @Test
    public void onFailure_ignoresIfIoException_endpoint() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.ENDPOINT_LEVEL);
        IOException exception = new IOException();

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void onFailure_ignoresIfIoException_sticky() {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.STICKY);
        IOException exception = new IOException();

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @ParameterizedTest
    @EnumSource(Behavior.class)
    public void onFailure_ignoresForNonIoExceptions(Behavior behavior) {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(behavior);
        RuntimeException exception = new RuntimeException();

        double max = limiter.getLimit();
        limiter.acquire(LimitEnforcement.DEFAULT_ENABLED).get().onFailure(exception);
        assertThat(limiter.getLimit()).isEqualTo(max);
    }

    @Test
    public void acquire_doesNotReleaseMorePermitsThanLimit() throws ExecutionException, InterruptedException {
        CautiousIncreaseAggressiveDecreaseConcurrencyLimiter limiter = limiter(Behavior.HOST_LEVEL);

        int max = (int) limiter.getLimit();
        Optional<CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit> latestPermit;
        for (int i = 0; i < max - 1; ++i) {
            latestPermit = limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
            assertThat(latestPermit).isPresent();
        }

        latestPermit = limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
        assertThat(latestPermit).isPresent();
        assertThat(limiter.acquire(LimitEnforcement.DEFAULT_ENABLED)).isEmpty();
        latestPermit.get().ignore();

        // Now let's have some threads fight for that last remaining permit.
        int numTasks = 8;
        int numIterations = 10_000;
        CountDownLatch latch = new CountDownLatch(numTasks);
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(numTasks));
        List<ListenableFuture<?>> futures = new ArrayList<>();
        try {
            IntStream.range(0, numTasks).forEach(_ignore -> {
                futures.add(executor.submit(() -> {
                    latch.countDown();
                    Uninterruptibles.awaitUninterruptibly(latch);

                    for (int i = 0; i < numIterations; i++) {
                        Optional<Permit> acquire = limiter.acquire(LimitEnforcement.DEFAULT_ENABLED);
                        if (acquire.isPresent()) {
                            assertThat(acquire.get().inFlightSnapshot()).isLessThanOrEqualTo(max);
                            acquire.get().ignore();
                        }
                    }
                }));
            });

            Futures.whenAllSucceed(futures)
                    .run(() -> {}, MoreExecutors.directExecutor())
                    .get();
        } finally {
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executor, Duration.ofSeconds(5)))
                    .isTrue();
        }
    }
}
