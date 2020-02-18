/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RoundRobinChannelTest {

    private static final Optional<ListenableFuture<Response>> CHANNEL_A_RESPONSE = Optional.of(SettableFuture.create());
    private static final Optional<ListenableFuture<Response>> CHANNEL_B_RESPONSE = Optional.of(SettableFuture.create());
    private static final Optional<ListenableFuture<Response>> UNAVAILABLE = Optional.empty();

    @Mock
    private LimitedChannel channelA;

    @Mock
    private LimitedChannel channelB;

    @Mock
    private Endpoint endpoint;

    @Mock
    private Request request;

    private RoundRobinChannel loadBalancer;

    @BeforeEach
    public void before() {
        loadBalancer = new RoundRobinChannel(ImmutableList.of(channelA, channelB));

        lenient().when(channelA.maybeExecute(endpoint, request)).thenReturn(CHANNEL_A_RESPONSE);
        lenient().when(channelB.maybeExecute(endpoint, request)).thenReturn(CHANNEL_B_RESPONSE);
    }

    @Test
    public void testRoundRobins() {
        assertThat(loadBalancer.maybeExecute(endpoint, request)).isEqualTo(CHANNEL_A_RESPONSE);
        assertThat(loadBalancer.maybeExecute(endpoint, request)).isEqualTo(CHANNEL_B_RESPONSE);
        assertThat(loadBalancer.maybeExecute(endpoint, request)).isEqualTo(CHANNEL_A_RESPONSE);
    }

    @Test
    public void testIgnoresUnavailableChannels() {
        when(channelA.maybeExecute(endpoint, request)).thenReturn(UNAVAILABLE);

        assertThat(loadBalancer.maybeExecute(endpoint, request)).isEqualTo(CHANNEL_B_RESPONSE);
        assertThat(loadBalancer.maybeExecute(endpoint, request)).isEqualTo(CHANNEL_B_RESPONSE);
    }

    @Test
    public void testNoChannelsAvailable() {
        when(channelA.maybeExecute(endpoint, request)).thenReturn(UNAVAILABLE);
        when(channelB.maybeExecute(endpoint, request)).thenReturn(UNAVAILABLE);

        assertThat(loadBalancer.maybeExecute(endpoint, request)).isEmpty();
    }

    @Test
    public void testNoChannelsConfigured() {
        loadBalancer = new RoundRobinChannel(ImmutableList.of());

        assertThat(loadBalancer.maybeExecute(endpoint, request)).isEmpty();
    }
}
