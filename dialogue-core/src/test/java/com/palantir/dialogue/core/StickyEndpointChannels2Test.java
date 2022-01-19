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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.palantir.dialogue.TestEndpoint;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import com.palantir.logsafe.Preconditions;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
public final class StickyEndpointChannels2Test {

    private final Endpoint endpoint = TestEndpoint.GET;

    @Mock
    private LimitedChannel nodeSelectionChannel;

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

        TestHarness request1 =
                new TestHarness(channel1).expectAddStickyTokenRequest().execute();
        TestHarness request2 =
                new TestHarness(channel2).expectAddStickyTokenRequest().execute();

        assertThat(QueueAttachments.getQueueOverride(request1.request))
                .isNotEqualTo(QueueAttachments.getQueueOverride(request2.request));
    }

    @Test
    public void requests_queue_up_and_propagate_sticky_target() {
        Channel channel = sticky.get();

        TestHarness request1 =
                new TestHarness(channel).expectAddStickyTokenRequest().execute().assertNotDone();

        TestHarness request2 =
                new TestHarness(channel).expectStickyRequest(request1).execute().assertNotDone();

        request1.setResponse().assertDoneSuccessful();
        request2.setResponse().assertDoneSuccessful();
    }

    @Test
    public void on_failure_next_request_is_executed() {
        Channel channel = sticky.get();

        TestHarness request1 =
                new TestHarness(channel).expectAddStickyTokenRequest().execute().assertNotDone();

        TestHarness request2 =
                new TestHarness(channel).expectAddStickyTokenRequest().execute().assertNotDone();

        request1.setException().assertDoneFailed();
        request2.assertNotDone();

        request2.setResponse().assertDoneSuccessful();
    }

    @Test
    public void cancel_queued_request_does_not_cancel_in_flight() {
        Channel channel = sticky.get();

        TestHarness request1 =
                new TestHarness(channel).expectAddStickyTokenRequest().execute().assertNotDone();

        TestHarness request2 =
                new TestHarness(channel).expectNoRequest().execute().assertNotDone();

        request2.cancelResponse().assertDoneCancelled();

        request1.assertNotDone();
    }

    @Test
    public void cancel_queued_request_does_not_cancel_second_queued() {
        Channel channel = sticky.get();

        TestHarness request1 =
                new TestHarness(channel).expectAddStickyTokenRequest().execute().assertNotDone();

        TestHarness request2 = new TestHarness(channel).execute().assertNotDone();

        TestHarness request3 = new TestHarness(channel).execute().assertNotDone();

        request2.cancelResponse().assertDoneCancelled();

        request1.assertNotDone();

        request3.assertNotDone();
    }

    @Test
    public void queued_request_only_attempted_once() {
        Channel channel = sticky.get();

        TestHarness request1 =
                new TestHarness(channel).expectAddStickyTokenRequest().execute().assertNotDone();

        TestHarness request2 = new TestHarness(channel)
                .expectStickyRequest(request1, 1)
                .execute()
                .assertNotDone();

        request1.setResponse();
        request2.setException();
        request2.assertDoneFailed();
    }

    @Test
    public void cancel_in_flight_request_does_not_cancel_queued() {
        Channel channel = sticky.get();

        TestHarness request1 =
                new TestHarness(channel).expectAddStickyTokenRequest().execute().assertNotDone();

        TestHarness request2 =
                new TestHarness(channel).expectAddStickyTokenRequest().execute().assertNotDone();

        request1.cancelResponse().assertDoneCancelled();

        request2.assertNotDone();
    }

    private final class TestHarness {
        Channel channel;
        Request request = Request.builder().build();
        TestResponse response = TestResponse.withBody(null);
        RuntimeException runtimeException = new RuntimeException();

        SettableFuture<Response> responseSettableFuture = SettableFuture.create();

        @Nullable
        ListenableFuture<Response> responseListenableFuture;

        private TestHarness(Channel channel) {
            this.channel = channel;
        }

        TestHarness expectAddStickyTokenRequest() {
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
            return this;
        }

        TestHarness expectStickyRequest(TestHarness responseTestHarness, int maxTimes) {
            AtomicInteger count = new AtomicInteger();
            when(endpointChannel.execute(request)).thenAnswer((Answer<ListenableFuture<Response>>) invocation -> {
                Request actualRequest = invocation.getArgument(0);
                assertThat(actualRequest).isEqualTo(request);
                assertThat(count.incrementAndGet()).isLessThanOrEqualTo(maxTimes);
                assertThat(responseTestHarness.responseListenableFuture).isDone();
                assertThat(Futures.getDone(responseTestHarness.responseListenableFuture)
                                .attachments()
                                .getOrDefault(StickyAttachments.STICKY_TOKEN, null))
                        .isEqualTo(request.attachments().getOrDefault(StickyAttachments.STICKY, null));
                return responseSettableFuture;
            });
            return this;
        }

        TestHarness expectStickyRequest(TestHarness responseTestHarness) {
            expectStickyRequest(responseTestHarness, 1);
            return this;
        }

        TestHarness expectNoRequest() {
            lenient().when(endpointChannel.execute(request)).thenThrow(new RuntimeException());
            return this;
        }

        TestHarness execute() {
            responseListenableFuture = channel.execute(endpoint, request);
            return this;
        }

        TestHarness assertNotDone() {
            assertThat(responseListenableFuture).isNotDone();
            return this;
        }

        TestHarness assertDoneSuccessful() {
            assertThat(responseListenableFuture).isDone();
            try {
                assertThat(Futures.getDone(Preconditions.checkNotNull(responseListenableFuture, "setup failure")))
                        .isEqualTo(response);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            return this;
        }

        TestHarness assertDoneFailed() {
            assertThat(responseListenableFuture).isDone();
            assertThatThrownBy(() ->
                            Futures.getDone(Preconditions.checkNotNull(responseListenableFuture, "setup failure")))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCause(runtimeException);
            return this;
        }

        TestHarness assertDoneCancelled() {
            assertThat(responseListenableFuture).isCancelled();
            return this;
        }

        TestHarness setResponse() {
            responseSettableFuture.set(response);
            return this;
        }

        TestHarness setException() {
            responseSettableFuture.setException(runtimeException);
            return this;
        }

        TestHarness cancelResponse() {
            assertThat(responseListenableFuture.cancel(true)).isTrue();
            return this;
        }
    }
}
