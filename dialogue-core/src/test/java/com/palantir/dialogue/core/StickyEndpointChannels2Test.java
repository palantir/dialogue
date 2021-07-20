/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class StickyEndpointChannels2Test {

    @Mock
    private Endpoint endpoint;

    @Mock
    private LimitedChannel delegate;

    @Mock
    private EndpointChannelFactory endpointChannelFactory;

    @Mock
    private EndpointChannel endpointChannel;

    @Mock
    private Config config;

    @Mock
    private ClientConfiguration clientConfiguration;

    private Supplier<Channel> sticky;

    @BeforeEach
    public void beforeEach() {
        when(config.channelName()).thenReturn("channel");
        when(config.clientConf()).thenReturn(clientConfiguration);
        lenient().when(endpointChannelFactory.endpoint(any())).thenReturn(endpointChannel);
        when(clientConfiguration.taggedMetricRegistry()).thenReturn(new DefaultTaggedMetricRegistry());
        sticky = StickyEndpointChannels2.create(config, delegate, endpointChannelFactory);
    }

    @Test
    public void channel_implements_channel_and_endpointchannel() {
        assertThat(sticky.get()).isInstanceOf(Channel.class).isInstanceOf(EndpointChannelFactory.class);
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void channels_get_unique_queues() {
        Channel channel1 = sticky.get();
        Channel channel2 = sticky.get();

        Request request1 = Request.builder().build();
        expectRequest(request1);
        channel1.execute(endpoint, request1);

        Request request2 = Request.builder().build();
        expectRequest(request2);
        channel2.execute(endpoint, request2);

        assertThat(QueueAttachments.getQueueOverride(request1))
                .isNotEqualTo(QueueAttachments.getQueueOverride(request2));
    }

    @Test
    public void requests_propagate_sticky_target() {}

    private SettableFuture<Response> expectRequest(Request request) {
        SettableFuture<Response> responseSettableFuture = SettableFuture.create();
        when(endpointChannel.execute(request)).thenReturn(responseSettableFuture);
        return responseSettableFuture;
    }
}
