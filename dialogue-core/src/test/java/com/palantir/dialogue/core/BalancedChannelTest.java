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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalancedChannelTest {
    private Random random = new Random(12388544234L);

    @Mock
    LimitedChannel chan1;

    @Mock
    LimitedChannel chan2;

    @Mock
    Request request;

    private Endpoint endpoint = TestEndpoint.GET;
    private BalancedChannel channel;

    @Mock
    Ticker clock;

    @BeforeEach
    public void before() {
        channel = new BalancedChannel(ImmutableList.of(chan1, chan2), random, clock);
    }

    @Test
    void when_one_channel_is_in_use_prefer_the_other() {
        set200(chan1);
        SettableFuture<Response> settableFuture = SettableFuture.create();
        when(chan2.maybeExecute(any(), any())).thenReturn(Optional.of(settableFuture));

        for (int i = 0; i < 200; i++) {
            channel.maybeExecute(endpoint, request);
        }
        verify(chan1, times(199)).maybeExecute(any(), any());
        verify(chan2, times(1)).maybeExecute(any(), any());
    }

    @Test
    void when_both_channels_are_free_we_get_roughly_fair_tiebreaking() {
        set200(chan1);
        set200(chan2);

        for (int i = 0; i < 200; i++) {
            channel.maybeExecute(endpoint, request);
        }
        verify(chan1, times(99)).maybeExecute(any(), any());
        verify(chan2, times(101)).maybeExecute(any(), any());
    }

    @Test
    void when_channels_refuse_try_all_then_give_up() {
        when(chan1.maybeExecute(any(), any())).thenReturn(Optional.empty());
        when(chan2.maybeExecute(any(), any())).thenReturn(Optional.empty());

        assertThat(channel.maybeExecute(endpoint, request)).isNotPresent();
        verify(chan1, times(1)).maybeExecute(any(), any());
        verify(chan2, times(1)).maybeExecute(any(), any());
    }

    @Test
    void sorting() {
        BalancedChannel.MutableChannelWithStats chan100 = new BalancedChannel.MutableChannelWithStats(chan1, clock);
        chan100.inflight.set(100);
        BalancedChannel.MutableChannelWithStats chan100failed = new BalancedChannel.MutableChannelWithStats(chan1, clock);
        chan100failed.inflight.set(100);
        chan100failed.recentFailures.update(1);
        BalancedChannel.MutableChannelWithStats chan50 = new BalancedChannel.MutableChannelWithStats(chan1, clock);
        chan50.inflight.set(50);
        chan50.recentFailures.update(1);
        BalancedChannel.MutableChannelWithStats chan101 = new BalancedChannel.MutableChannelWithStats(chan1, clock);
        chan101.inflight.set(101);

        assertThat(Stream.of(chan100failed, chan100, chan50, chan101)
                        .map(c -> c.immutableSnapshot())
                        .sorted(BalancedChannel.RANKING_HEURISTIC)
                        .map(c -> c.delegate))
                .describedAs("Failures are considered very bad")
                .containsExactly(chan50, chan100, chan101, chan100failed);
    }

    private static void set200(LimitedChannel chan) {
        when(chan.maybeExecute(any(), any()))
                .thenReturn(Optional.of(Futures.immediateFuture(new TestResponse().code(200))));
    }
}
