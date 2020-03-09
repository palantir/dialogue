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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public class RandomSelectionChannelTest {

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

    @Mock
    private Random random;

    private RandomSelectionChannel loadBalancer;

    @BeforeEach
    public void before() {
        AtomicInteger index = new AtomicInteger();
        lenient().when(random.nextInt(anyInt())).thenAnswer((Answer<Integer>) invocation -> {
            int input = invocation.getArgument(0);
            return index.getAndIncrement() % input;
        });
        loadBalancer = new RandomSelectionChannel(ImmutableList.of(channelA, channelB), random);

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
        assertThatThrownBy(() -> new RandomSelectionChannel(ImmutableList.of(), random))
                .isInstanceOf(SafeIllegalArgumentException.class);
    }
}
