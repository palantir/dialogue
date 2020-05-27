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
        verify(chan1, times(299)).maybeExecute(eq(endpoint), any());
        verify(chan2, times(301)).maybeExecute(eq(endpoint), any());
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
            assertThat(channel.getScores())
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
            assertThat(channel.getScores())
                    .describedAs("%s %s: Scores not affected yet %s", i, Duration.ofNanos(clock.read()), channel)
                    .containsExactly(0, 0);
            incrementClockBy(Duration.ofMillis(50));
        }
        channel.maybeExecute(endpoint, request);
        assertThat(channel.getScores())
                .describedAs("%s: Constant 4xxs did move the needle %s", Duration.ofNanos(clock.read()), channel)
                .containsExactly(1, 0);

        incrementClockBy(Duration.ofSeconds(5));

        assertThat(channel.getScores())
                .describedAs(
                        "%s: We quickly forget about 4xxs and go back to fair shuffling %s",
                        Duration.ofNanos(clock.read()), channel)
                .containsExactly(0, 0);
    }

    @Test
    void rtt_just_remembers_the_min() {
        BalancedNodeSelectionStrategyChannel.RoundTripTimeMeasurement rtt =
                new BalancedNodeSelectionStrategyChannel.RoundTripTimeMeasurement();
        rtt.addMeasurement(3);
        assertThat(rtt.getNanos()).isEqualTo(3);
        rtt.addMeasurement(1);
        rtt.addMeasurement(2);
        assertThat(rtt.getNanos()).isEqualTo(1);

        rtt.addMeasurement(500);
        assertThat(rtt.getNanos()).isEqualTo(1);
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
