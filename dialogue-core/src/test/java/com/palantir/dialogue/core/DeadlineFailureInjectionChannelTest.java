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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.deadlines.DeadlineExpiredException;
import com.palantir.deadlines.Deadlines.Enforcement;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class DeadlineFailureInjectionChannelTest {

    private static final String CHANNEL_NAME = "channel";

    /** Always selects the request for failure. */
    private static final IntUnaryOperator ALWAYS_SELECTS = _bound -> 0;

    /** Never selects the request for failure. */
    private static final IntUnaryOperator NEVER_SELECTS = _bound -> 1;

    private final Channel delegate = (_endpoint, _request) -> Futures.immediateCancelledFuture();

    private static Optional<DeadlineFailureInjectionConfiguration> enabled() {
        return DeadlineFailureInjectionConfiguration.fromEnvironmentValue("true");
    }

    private static Optional<DeadlineFailureInjectionConfiguration> disabled() {
        return Optional.empty();
    }

    @Test
    void wraps_delegate_when_feature_is_enabled_and_deadlines_are_enforced() {
        assertThat(DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                        delegate, CHANNEL_NAME, Enforcement.ENFORCE, enabled(), ALWAYS_SELECTS, () -> 0L))
                .isInstanceOf(DeadlineFailureInjectionChannel.class);
    }

    @Test
    void returns_existing_delegate_when_feature_is_disabled() {
        assertThat(DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                        delegate,
                        CHANNEL_NAME,
                        Enforcement.ENFORCE,
                        disabled(),
                        _bound -> {
                            throw new AssertionError("random should not be used");
                        },
                        () -> {
                            throw new AssertionError("ticker should not be used");
                        }))
                .isSameAs(delegate);
    }

    @Test
    void returns_existing_delegate_when_deadlines_are_not_enforced() {
        assertThat(DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                        delegate, CHANNEL_NAME, Enforcement.DISABLE, enabled(), ALWAYS_SELECTS, () -> 0L))
                .isSameAs(delegate);
        assertThat(DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                        delegate, CHANNEL_NAME, Enforcement.DEFER, enabled(), ALWAYS_SELECTS, () -> 0L))
                .isSameAs(delegate);
    }

    @Test
    void injects_failure_at_random() {
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate, CHANNEL_NAME, Enforcement.ENFORCE, enabled(), ALWAYS_SELECTS, () -> 0L);

        ListenableFuture<Response> response =
                result.execute(TestEndpoint.GET, Request.builder().build());
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(response::get)
                .withCauseInstanceOf(DeadlineExpiredException.External.class);
    }

    @Test
    void requests_not_selected_for_failure_reach_delegate() {
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate, CHANNEL_NAME, Enforcement.ENFORCE, enabled(), NEVER_SELECTS, () -> {
                    throw new AssertionError("no need for clock if request isn't selected");
                });

        assertThat(result.execute(TestEndpoint.GET, Request.builder().build())).isCancelled();
    }

    @Test
    void caps_failures_to_the_default_interval() {
        AtomicLong nanoTime = new AtomicLong();
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate, CHANNEL_NAME, Enforcement.ENFORCE, enabled(), ALWAYS_SELECTS, nanoTime::get);

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(
                        () -> result.execute(TestEndpoint.GET, Request.builder().build())
                                .get())
                .withCauseInstanceOf(DeadlineExpiredException.External.class);
        assertThat(result.execute(TestEndpoint.GET, Request.builder().build())).isCancelled();

        nanoTime.set(DeadlineFailureInjectionConfiguration.DEFAULT_MINIMUM_FAILURE_INTERVAL.toNanos());
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(
                        () -> result.execute(TestEndpoint.GET, Request.builder().build())
                                .get())
                .withCauseInstanceOf(DeadlineExpiredException.External.class);
    }

    @Test
    void caps_failures_to_the_configured_interval() {
        AtomicLong nanoTime = new AtomicLong();
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate,
                CHANNEL_NAME,
                Enforcement.ENFORCE,
                DeadlineFailureInjectionConfiguration.fromEnvironmentValue("1,1m"),
                ALWAYS_SELECTS,
                nanoTime::get);

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(
                        () -> result.execute(TestEndpoint.GET, Request.builder().build())
                                .get())
                .withCauseInstanceOf(DeadlineExpiredException.External.class);

        // Still capped just before the configured minute elapses, unlike the fifteen minute default.
        nanoTime.set(Duration.ofSeconds(59).toNanos());
        assertThat(result.execute(TestEndpoint.GET, Request.builder().build())).isCancelled();

        nanoTime.set(Duration.ofMinutes(1).toNanos());
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(
                        () -> result.execute(TestEndpoint.GET, Request.builder().build())
                                .get())
                .withCauseInstanceOf(DeadlineExpiredException.External.class);
    }

    @Test
    void a_zero_interval_fails_every_selected_request() {
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate,
                CHANNEL_NAME,
                Enforcement.ENFORCE,
                DeadlineFailureInjectionConfiguration.fromEnvironmentValue("1,0"),
                ALWAYS_SELECTS,
                () -> {
                    throw new AssertionError("clock should not be read when the cap is disabled");
                });

        for (int i = 0; i < 3; i++) {
            assertThatExceptionOfType(ExecutionException.class)
                    .isThrownBy(() -> result.execute(
                                    TestEndpoint.GET, Request.builder().build())
                            .get())
                    .withCauseInstanceOf(DeadlineExpiredException.External.class);
        }
    }

    @Test
    void configured_rate_bounds_the_random_selection() {
        AtomicInteger observedBound = new AtomicInteger();
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate,
                CHANNEL_NAME,
                Enforcement.ENFORCE,
                DeadlineFailureInjectionConfiguration.fromEnvironmentValue("4"),
                bound -> {
                    observedBound.set(bound);
                    return 1;
                },
                () -> 0L);

        assertThat(result.execute(TestEndpoint.GET, Request.builder().build())).isCancelled();
        assertThat(observedBound).hasValue(4);
    }

    @Test
    void default_rate_is_used_when_enabled_without_an_explicit_rate() {
        AtomicInteger observedBound = new AtomicInteger();
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate,
                CHANNEL_NAME,
                Enforcement.ENFORCE,
                enabled(),
                bound -> {
                    observedBound.set(bound);
                    return 1;
                },
                () -> 0L);

        assertThat(result.execute(TestEndpoint.GET, Request.builder().build())).isCancelled();
        assertThat(observedBound).hasValue(DeadlineFailureInjectionConfiguration.DEFAULT_ONE_IN_EVERY);
    }
}
