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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("DirectInvocationOnMock")
@ExtendWith(MockitoExtension.class)
class BalancedNodeSelectionStrategyChannelTest {
    private Random random = new Random(12388544234L);

    @Mock
    LimitedChannel chan1;

    @Mock
    LimitedChannel chan2;

    private Request request = Request.builder().build();
    private Endpoint endpoint = TestEndpoint.GET;
    private BalancedNodeSelectionStrategyChannel channel;
    private BalancedNodeSelectionStrategyChannel rttChannel;

    @Mock
    Ticker clock;

    @BeforeEach
    public void before() {
        channel = new BalancedNodeSelectionStrategyChannel(
                ImmutableList.of(chan1, chan2), random, clock, new DefaultTaggedMetricRegistry(), "channelName");
        rttChannel = new BalancedNodeSelectionStrategyChannel(
                ImmutableList.of(chan1, chan2), random, clock, new DefaultTaggedMetricRegistry(), "channelName");
    }

    @Test
    void when_one_channel_is_in_use_prefer_the_other() {
        set200(chan1);
        SettableFuture<Response> settableFuture = SettableFuture.create();
        when(chan2.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(Optional.of(settableFuture));

        for (int i = 0; i < 200; i++) {
            channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED);
        }
        verify(chan1, times(199)).maybeExecute(eq(endpoint), any(), eq(LimitEnforcement.DEFAULT_ENABLED));
        verify(chan2, times(1)).maybeExecute(eq(endpoint), any(), eq(LimitEnforcement.DEFAULT_ENABLED));
    }

    @Test
    void when_both_channels_are_free_we_get_roughly_fair_tiebreaking() {
        set200(chan1);
        set200(chan2);

        for (int i = 0; i < 200; i++) {
            channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED);
        }
        verify(chan1, times(99)).maybeExecute(eq(endpoint), any(), eq(LimitEnforcement.DEFAULT_ENABLED));
        verify(chan2, times(101)).maybeExecute(eq(endpoint), any(), eq(LimitEnforcement.DEFAULT_ENABLED));
    }

    @Test
    void when_channels_refuse_try_all_then_give_up() {
        when(chan1.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(Optional.empty());
        when(chan2.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(Optional.empty());

        assertThat(channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED))
                .isNotPresent();
        verify(chan1, times(1)).maybeExecute(eq(endpoint), any(), eq(LimitEnforcement.DEFAULT_ENABLED));
        verify(chan2, times(1)).maybeExecute(eq(endpoint), any(), eq(LimitEnforcement.DEFAULT_ENABLED));
    }

    @Test
    void a_single_4xx_doesnt_move_the_needle() {
        when(chan1.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(http(400))
                .thenReturn(http(200));
        when(chan2.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(http(200));

        for (long start = clock.read();
                clock.read() < start + Duration.ofSeconds(10).toNanos();
                incrementClockBy(Duration.ofMillis(50))) {
            channel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED);
            assertThat(channel.getScoresForTesting())
                    .describedAs("A single 400 at the beginning isn't enough to impact scores", channel)
                    .containsExactly(0, 0);
        }

        verify(chan1, times(99)).maybeExecute(eq(endpoint), any(), eq(LimitEnforcement.DEFAULT_ENABLED));
        verify(chan2, times(101)).maybeExecute(eq(endpoint), any(), eq(LimitEnforcement.DEFAULT_ENABLED));
    }

    @Test
    void constant_4xxs_do_eventually_move_the_needle_but_we_go_back_to_fair_distribution() {
        when(chan1.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(http(400));
        when(chan2.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(http(200));

        for (int i = 0; i < 11; i++) {
            rttChannel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED);
            assertThat(rttChannel.getScoresForTesting())
                    .describedAs("%s %s: Scores not affected yet %s", i, Duration.ofNanos(clock.read()), rttChannel)
                    .containsExactly(0, 0);
            incrementClockBy(Duration.ofMillis(50));
        }
        rttChannel.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED);
        assertThat(rttChannel.getScoresForTesting())
                .describedAs("%s: Constant 4xxs did move the needle %s", Duration.ofNanos(clock.read()), rttChannel)
                .containsExactly(1, 0);

        incrementClockBy(Duration.ofSeconds(5));

        assertThat(rttChannel.getScoresForTesting())
                .describedAs(
                        "%s: We quickly forget about 4xxs and go back to fair shuffling %s",
                        Duration.ofNanos(clock.read()), rttChannel)
                .containsExactly(0, 0);
    }

    @ParameterizedTest
    @EnumSource(LimitEnforcement.class)
    public void skiplimits_passthrough(LimitEnforcement limitEnforcement) {
        when(chan1.maybeExecute(any(), any(), eq(limitEnforcement))).thenReturn(http(200));
        assertThat(channel.maybeExecute(endpoint, request, limitEnforcement)).isPresent();
    }

    @Test
    public void stickyToken_requests_maintain_scores() throws ExecutionException {
        SettableFuture<Response> initialRequestFuture = SettableFuture.create();
        SettableFuture<Response> stickyRequestFuture = SettableFuture.create();
        when(chan2.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(Optional.of(initialRequestFuture))
                .thenReturn(Optional.of(stickyRequestFuture));

        request = Request.builder().build();
        StickyAttachments.requestStickyToken(request);
        ListenableFuture<Response> responseFuture = channel.maybeExecute(
                        endpoint, request, LimitEnforcement.DEFAULT_ENABLED)
                .get();

        assertThat(channel.getScoresForTesting())
                .describedAs("initial request maintains scores")
                .containsExactly(0, 1);

        initialRequestFuture.set(TestResponse.withBody(null));
        Consumer<Request> requestConsumer = StickyAttachments.copyStickyTarget(Futures.getDone(responseFuture));

        assertThat(channel.getScoresForTesting())
                .describedAs("complete initial request updates scores")
                .containsExactly(0, 0);

        request = Request.builder().build();
        requestConsumer.accept(request);
        assertThat(StickyAttachments.maybeExecuteOnSticky(null, endpoint, request, LimitEnforcement.DEFAULT_ENABLED))
                .isPresent();

        assertThat(channel.getScoresForTesting())
                .describedAs("Follow on request maintains scores")
                .containsExactly(0, 1);
        stickyRequestFuture.set(TestResponse.withBody(null));
        assertThat(channel.getScoresForTesting())
                .describedAs("Complete follow on request updates scores")
                .containsExactly(0, 0);
    }

    private static void set200(LimitedChannel chan) {
        when(chan.maybeExecute(any(), any(), eq(LimitEnforcement.DEFAULT_ENABLED)))
                .thenReturn(http(200));
    }

    private static Optional<ListenableFuture<Response>> http(int value) {
        return Optional.of(Futures.immediateFuture(new TestResponse().code(value)));
    }

    private void incrementClockBy(Duration duration) {
        long before = clock.read();
        when(clock.read()).thenReturn(before + duration.toNanos());
    }
}
