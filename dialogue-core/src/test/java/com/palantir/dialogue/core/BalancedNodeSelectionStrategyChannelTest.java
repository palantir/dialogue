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
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalancedNodeSelectionStrategyChannelTest {
    private Random random = new Random(12388544234L);

    @Mock
    LimitedChannel chan1;

    @Mock
    LimitedChannel chan2;

    @Mock
    Request request;

    private Endpoint endpoint = TestEndpoint.GET;
    private BalancedNodeSelectionStrategyChannel channel;

    @Mock
    Ticker clock;

    @BeforeEach
    public void before() {
        channel = new BalancedNodeSelectionStrategyChannel(
                ImmutableList.of(chan1, chan2), random, clock, new DefaultTaggedMetricRegistry(), "channelName");
    }

    @Test
    void when_one_channel_is_in_use_prefer_the_other() {
        set200(chan1);
        SettableFuture<Response> settableFuture = SettableFuture.create();
        when(chan2.maybeExecute(any(), any())).thenReturn(Optional.of(settableFuture));

        for (int i = 0; i < 200; i++) {
            channel.maybeExecute(endpoint, request);
        }
        verify(chan1, times(199)).maybeExecute(eq(endpoint), any());
        verify(chan2, times(1)).maybeExecute(eq(endpoint), any());
    }

    @Test
    void when_both_channels_are_free_we_get_roughly_fair_tiebreaking() {
        set200(chan1);
        set200(chan2);

        for (int i = 0; i < 200; i++) {
            channel.maybeExecute(endpoint, request);
        }
        verify(chan1, times(99)).maybeExecute(eq(endpoint), any());
        verify(chan2, times(101)).maybeExecute(eq(endpoint), any());
    }

    @Test
    void when_channels_refuse_try_all_then_give_up() {
        when(chan1.maybeExecute(any(), any())).thenReturn(Optional.empty());
        when(chan2.maybeExecute(any(), any())).thenReturn(Optional.empty());

        assertThat(channel.maybeExecute(endpoint, request)).isNotPresent();
        verify(chan1, times(1)).maybeExecute(eq(endpoint), any());
        verify(chan2, times(1)).maybeExecute(eq(endpoint), any());
    }

    @Test
    void a_single_4xx_doesnt_move_the_needle() {
        when(chan1.maybeExecute(any(), any())).thenReturn(http(400)).thenReturn(http(200));
        when(chan2.maybeExecute(any(), any())).thenReturn(http(200));

        for (long start = clock.read();
                clock.read() < start + Duration.ofSeconds(10).toNanos();
                incrementClockBy(Duration.ofMillis(50))) {
            channel.maybeExecute(endpoint, request);
            assertThat(channel.getScoresForTesting().map(c -> c.getScore()))
                    .describedAs("A single 400 at the beginning isn't enough to impact scores", channel)
                    .containsExactly(0, 0);
        }

        verify(chan1, times(99)).maybeExecute(eq(endpoint), any());
        verify(chan2, times(101)).maybeExecute(eq(endpoint), any());
    }

    @Test
    void constant_4xxs_do_eventually_move_the_needle_but_we_go_back_to_fair_distribution() {
        when(chan1.maybeExecute(any(), any())).thenReturn(http(400));
        when(chan2.maybeExecute(any(), any())).thenReturn(http(200));

        for (int i = 0; i < 11; i++) {
            channel.maybeExecute(endpoint, request);
            assertThat(channel.getScoresForTesting().map(c -> c.getScore()))
                    .describedAs("%s %s: Scores not affected yet %s", i, Duration.ofNanos(clock.read()), channel)
                    .containsExactly(0, 0);
            incrementClockBy(Duration.ofMillis(50));
        }
        channel.maybeExecute(endpoint, request);
        assertThat(channel.getScoresForTesting().map(c -> c.getScore()))
                .describedAs("%s: Constant 4xxs did move the needle %s", Duration.ofNanos(clock.read()), channel)
                .containsExactly(1, 0);

        incrementClockBy(Duration.ofSeconds(5));

        assertThat(channel.getScoresForTesting().map(c -> c.getScore()))
                .describedAs(
                        "%s: We quickly forget about 4xxs and go back to fair shuffling %s",
                        Duration.ofNanos(clock.read()), channel)
                .containsExactly(0, 0);
    }

    @Test
    void rtt_is_measured_and_can_influence_choices() {
        incrementClockBy(Duration.ofHours(1));

        // when(chan1.maybeExecute(eq(endpoint), any())).thenReturn(http(200));
        when(chan2.maybeExecute(eq(endpoint), any())).thenReturn(http(200));

        SettableFuture<Response> chan1OptionsResponse = SettableFuture.create();
        SettableFuture<Response> chan2OptionsResponse = SettableFuture.create();
        RttSampler.RttEndpoint rttEndpoint = RttSampler.RttEndpoint.INSTANCE;
        when(chan1.maybeExecute(eq(rttEndpoint), any())).thenReturn(Optional.of(chan1OptionsResponse));
        when(chan2.maybeExecute(eq(rttEndpoint), any())).thenReturn(Optional.of(chan2OptionsResponse));

        channel.maybeExecute(endpoint, request);

        incrementClockBy(Duration.ofNanos(123));
        chan1OptionsResponse.set(new TestResponse().code(200));

        incrementClockBy(Duration.ofNanos(456));
        chan2OptionsResponse.set(new TestResponse().code(200));

        assertThat(channel.getScoresForTesting().map(c -> c.getScore()))
                .describedAs("The poor latency of channel2 imposes a small constant penalty in the score")
                .containsExactly(0, 3);

        for (int i = 0; i < 500; i++) {
            incrementClockBy(Duration.ofMillis(10));
            channel.maybeExecute(endpoint, request);
        }
        // rate limiter ensures a sensible amount of rtt sampling
        verify(chan1, times(6)).maybeExecute(eq(rttEndpoint), any());
        verify(chan2, times(6)).maybeExecute(eq(rttEndpoint), any());
    }

    @Test
    void when_rtt_measurements_are_limited_dont_freak_out() {
        incrementClockBy(Duration.ofHours(1));

        // when(chan1.maybeExecute(eq(endpoint), any())).thenReturn(http(200));
        when(chan2.maybeExecute(eq(endpoint), any())).thenReturn(http(200));

        RttSampler.RttEndpoint rttEndpoint = RttSampler.RttEndpoint.INSTANCE;
        when(chan1.maybeExecute(eq(rttEndpoint), any())).thenReturn(Optional.empty());
        when(chan2.maybeExecute(eq(rttEndpoint), any())).thenReturn(Optional.empty());

        channel.maybeExecute(endpoint, request);

        assertThat(channel.getScoresForTesting().map(c -> c.getScore())).containsExactly(0, 0);
    }

    @Test
    void when_rtt_measurements_havent_returned_yet_dont_freak_out() {
        incrementClockBy(Duration.ofHours(1));
        // when(chan1.maybeExecute(eq(endpoint), any())).thenReturn(http(200));
        when(chan2.maybeExecute(eq(endpoint), any())).thenReturn(http(200));

        RttSampler.RttEndpoint rttEndpoint = RttSampler.RttEndpoint.INSTANCE;
        when(chan1.maybeExecute(eq(rttEndpoint), any())).thenReturn(Optional.of(SettableFuture.create()));
        when(chan2.maybeExecute(eq(rttEndpoint), any())).thenReturn(Optional.of(SettableFuture.create()));

        for (int i = 0; i < 20; i++) {
            incrementClockBy(Duration.ofSeconds(5));
            channel.maybeExecute(endpoint, request);
        }

        assertThat(channel.getScoresForTesting().map(c -> c.getScore())).containsExactly(0, 0);
        verify(chan1, times(1)).maybeExecute(eq(rttEndpoint), any());
        verify(chan2, times(1)).maybeExecute(eq(rttEndpoint), any());
    }

    private static void set200(LimitedChannel chan) {
        when(chan.maybeExecute(any(), any())).thenReturn(http(200));
    }

    private static Optional<ListenableFuture<Response>> http(int value) {
        return Optional.of(Futures.immediateFuture(new TestResponse().code(value)));
    }

    private void incrementClockBy(Duration duration) {
        long before = clock.read();
        when(clock.read()).thenReturn(before + duration.toNanos());
    }
}
