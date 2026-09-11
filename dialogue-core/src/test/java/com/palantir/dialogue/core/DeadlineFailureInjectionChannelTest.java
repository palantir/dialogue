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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DeadlineFailureInjectionChannelTest {

    private final Channel delegate = (_endpoint, _request) -> Futures.immediateCancelledFuture();

    @Test
    void wraps_delegate_when_feature_is_enabled_and_deadlines_are_enforced() {
        assertThat(DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                        delegate, Enforcement.ENFORCE, true, () -> 0, () -> 0L))
                .isInstanceOf(DeadlineFailureInjectionChannel.class);
    }

    @Test
    void returns_existing_delegate_when_feature_is_disabled() {
        assertThat(DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                        delegate,
                        Enforcement.ENFORCE,
                        false,
                        () -> {
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
                        delegate, Enforcement.DISABLE, true, () -> 0, () -> 0L))
                .isSameAs(delegate);
        assertThat(DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                        delegate, Enforcement.DEFER, true, () -> 0, () -> 0L))
                .isSameAs(delegate);
    }

    @Test
    void injects_failure_at_random() {
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate, Enforcement.ENFORCE, true, () -> 0, () -> 0L);

        ListenableFuture<Response> response =
                result.execute(TestEndpoint.GET, Request.builder().build());
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(response::get)
                .withCauseInstanceOf(DeadlineExpiredException.External.class);
    }

    @Test
    void requests_not_selected_for_failure_reach_delegate() {
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate, Enforcement.ENFORCE, true, () -> 1, () -> {
                    throw new AssertionError("no need for clock if request isn't selected");
                });

        assertThat(result.execute(TestEndpoint.GET, Request.builder().build())).isCancelled();
    }

    @Test
    void caps_failures_to_one_every_fifteen_minutes() {
        AtomicLong nanoTime = new AtomicLong();
        Channel result = DeadlineFailureInjectionChannel.wrapDelegateIfEnabled(
                delegate, Enforcement.ENFORCE, true, () -> 0, nanoTime::get);

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(
                        () -> result.execute(TestEndpoint.GET, Request.builder().build())
                                .get())
                .withCauseInstanceOf(DeadlineExpiredException.External.class);
        assertThat(result.execute(TestEndpoint.GET, Request.builder().build())).isCancelled();

        nanoTime.set(Duration.ofMinutes(15).toNanos());
        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(
                        () -> result.execute(TestEndpoint.GET, Request.builder().build())
                                .get())
                .withCauseInstanceOf(DeadlineExpiredException.External.class);
    }
}
