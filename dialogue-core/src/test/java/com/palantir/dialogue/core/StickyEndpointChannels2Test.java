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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public final class StickyEndpointChannels2Test {

    @Mock
    private Endpoint endpoint;

    @Mock
    private LimitedChannel nodeSelectionChannel;

    // @Mock
    // private LimitedChannel stickyTarget;

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
        sticky = StickyEndpointChannels2.create(config, nodeSelectionChannel, endpointChannelFactory);
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
        expectAddStickyTokenRequest(request1);
        channel1.execute(endpoint, request1);

        Request request2 = Request.builder().build();
        expectAddStickyTokenRequest(request2);
        channel2.execute(endpoint, request2);

        assertThat(QueueAttachments.getQueueOverride(request1))
                .isNotEqualTo(QueueAttachments.getQueueOverride(request2));
    }

    @Test
    public void requests_propagate_sticky_target() throws ExecutionException {
        Channel channel = sticky.get();

        Request request1 = Request.builder().build();
        SettableFuture<Response> response1SettableFuture = expectAddStickyTokenRequest(request1);
        ListenableFuture<Response> response1ListenableFuture = channel.execute(endpoint, request1);
        assertThat(response1ListenableFuture).isNotDone();
        TestResponse testResponse1 = TestResponse.withBody(null);
        response1SettableFuture.set(testResponse1);

        assertThat(Futures.getDone(response1ListenableFuture)).isEqualTo(testResponse1);

        Request request2 = Request.builder().build();
        SettableFuture<Response> response2SettableFuture = expectStickyRequest(testResponse1, request2);
        ListenableFuture<Response> response2ListenableFuture = channel.execute(endpoint, request2);
        TestResponse testResponse2 = TestResponse.withBody(null);
        response2SettableFuture.set(testResponse2);
        assertThat(Futures.getDone(response2ListenableFuture)).isEqualTo(testResponse2);
    }

    private SettableFuture<Response> expectAddStickyTokenRequest(Request request) {
        SettableFuture<Response> responseSettableFuture = SettableFuture.create();
        when(endpointChannel.execute(request)).thenAnswer((Answer<ListenableFuture<Response>>) invocation -> {
            Request actualRequest = invocation.getArgument(0);
            assertThat(actualRequest).isEqualTo(request);
            LimitedChannel stickyTarget = mock(LimitedChannel.class);
            when(stickyTarget.maybeExecute(endpoint, request, LimitEnforcement.DEFAULT_ENABLED))
                    .thenReturn(Optional.of(responseSettableFuture));
            return StickyAttachments.maybeAddStickyToken(
                            stickyTarget, endpoint, actualRequest, LimitEnforcement.DEFAULT_ENABLED)
                    .get();
        });
        return responseSettableFuture;
    }

    private SettableFuture<Response> expectStickyRequest(Response response, Request request) {
        SettableFuture<Response> responseSettableFuture = SettableFuture.create();
        when(endpointChannel.execute(request)).thenAnswer((Answer<ListenableFuture<Response>>) invocation -> {
            Request actualRequest = invocation.getArgument(0);
            assertThat(actualRequest).isEqualTo(request);
            assertThat(response.attachments().getOrDefault(StickyAttachments.STICKY_TOKEN, null))
                    .isEqualTo(request.attachments().getOrDefault(StickyAttachments.STICKY, null));
            return responseSettableFuture;
        });
        return responseSettableFuture;
    }
}
