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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Gauge;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Behavior;
import com.palantir.dialogue.core.ConcurrencyLimitedChannel.ConcurrencyLimitedChannelInstrumentation;
import com.palantir.dialogue.core.ConcurrencyLimitedChannel.HostConcurrencyLimitedChannelInstrumentation;
import com.palantir.logsafe.exceptions.SafeIoException;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConcurrencyLimitedChannelTest {

    @Mock
    private Endpoint endpoint;

    @Mock
    private Request request;

    @Mock
    private Channel delegate;

    @Mock
    private CautiousIncreaseAggressiveDecreaseConcurrencyLimiter mockLimiter;

    @Spy
    private CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit hostPermit =
            new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(Behavior.HOST_LEVEL)
                    .acquire()
                    .get();

    @Spy
    private CautiousIncreaseAggressiveDecreaseConcurrencyLimiter.Permit endpointPermit =
            new CautiousIncreaseAggressiveDecreaseConcurrencyLimiter(Behavior.ENDPOINT_LEVEL)
                    .acquire()
                    .get();

    @Mock
    private Response response;

    private final TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
    private ConcurrencyLimitedChannel channel;
    private SettableFuture<Response> responseFuture;

    @BeforeEach
    public void before() {
        channel = new ConcurrencyLimitedChannel(
                delegate,
                mockLimiter,
                new HostConcurrencyLimitedChannelInstrumentation("channel", 0, mockLimiter, metrics));

        responseFuture = SettableFuture.create();
        lenient().when(delegate.execute(endpoint, request)).thenReturn(responseFuture);
    }

    @Test
    public void testLimiterAvailable_successfulRequest_host() {
        mockHostLimitAvailable();
        mockResponseCode(200);

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(hostPermit).success();
    }

    @Test
    public void testLimiterAvailable_successfulRequest_endpoint() {
        mockEndpointLimitAvailable();
        mockResponseCode(200);

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(endpointPermit).success();
    }

    @Test
    public void testLimiterAvailable_429isDropped_endpoint() {
        mockEndpointLimitAvailable();
        mockResponseCode(429);

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(endpointPermit).dropped();
    }

    @Test
    public void testLimiterAvailable_429isIgnored_host() {
        mockHostLimitAvailable();
        mockResponseCode(429);

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(hostPermit).ignore();
    }

    @Test
    public void testLimiterAvailable_runtimeExceptionIsIgnored_host() {
        mockHostLimitAvailable();
        responseFuture.setException(new IllegalStateException());

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(hostPermit).ignore();
    }

    @Test
    public void testLimiterAvailable_runtimeExceptionIsIgnored_endpoint() {
        mockEndpointLimitAvailable();
        responseFuture.setException(new IllegalStateException());

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(endpointPermit).ignore();
    }

    @Test
    public void testLimiterAvailable_ioExceptionIsDropped_host() {
        mockHostLimitAvailable();
        responseFuture.setException(new SafeIoException("failure"));

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(hostPermit).dropped();
    }

    @Test
    public void testLimiterAvailable_ioExceptionIsIgnored_endpoint() {
        mockEndpointLimitAvailable();
        responseFuture.setException(new SafeIoException("failure"));

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
        verify(endpointPermit).ignore();
    }

    @Test
    public void testUnavailable_host() {
        mockHostLimitUnavailable();

        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
        verifyNoMoreInteractions(hostPermit);
    }

    @Test
    public void testUnavailable_endpoint() {
        mockEndpointLimitUnavailable();

        assertThat(channel.maybeExecute(endpoint, request)).isEmpty();
        verifyNoMoreInteractions(endpointPermit);
    }

    @Test
    public void testWithDefaultLimiter() {
        channel = new ConcurrencyLimitedChannel(
                delegate,
                ConcurrencyLimitedChannel.createLimiter(Behavior.HOST_LEVEL),
                NopConcurrencyLimitedChannelInstrumentation.INSTANCE);

        assertThat(channel.maybeExecute(endpoint, request)).contains(responseFuture);
    }

    @Test
    void testGauges() {
        when(mockLimiter.getLimit()).thenReturn(21D);

        assertThat(getMax()).isEqualTo(21D);
    }

    private void mockResponseCode(int code) {
        when(response.code()).thenReturn(code);
        responseFuture.set(response);
    }

    private void mockHostLimitAvailable() {
        when(mockLimiter.acquire()).thenReturn(Optional.of(hostPermit));
    }

    private void mockHostLimitUnavailable() {
        when(mockLimiter.acquire()).thenReturn(Optional.empty());
    }

    private void mockEndpointLimitAvailable() {
        when(mockLimiter.acquire()).thenReturn(Optional.of(endpointPermit));
    }

    private void mockEndpointLimitUnavailable() {
        when(mockLimiter.acquire()).thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private Number getMax() {
        Gauge<Number> metric = (Gauge<Number>) metrics.getMetrics().entrySet().stream()
                .filter(entry -> entry.getKey().safeName().equals("dialogue.concurrencylimiter.max"))
                .findFirst()
                .get()
                .getValue();
        return metric.getValue();
    }

    enum NopConcurrencyLimitedChannelInstrumentation implements ConcurrencyLimitedChannelInstrumentation {
        INSTANCE;

        @Override
        public String channelNameForLogging() {
            return "nop";
        }
    }
}
