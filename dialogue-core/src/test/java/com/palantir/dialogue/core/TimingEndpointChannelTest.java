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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("FutureReturnValueIgnored")
public final class TimingEndpointChannelTest {

    private final TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
    private final Endpoint endpoint = TestEndpoint.POST;
    private final Timer success = ClientMetrics.of(registry)
            .response()
            .channelName("my-channel")
            .serviceName(endpoint.serviceName())
            .endpoint(endpoint.endpointName())
            .status("success")
            .build();
    private final Meter responseErrors = null;

    @Mock
    private EndpointChannel delegate;

    @Mock
    private Ticker ticker;

    private TimingEndpointChannel timingChannel;

    @BeforeEach
    public void before() {
        timingChannel = new TimingEndpointChannel(delegate, ticker, registry, "my-channel", endpoint);
    }

    @Test
    public void addsMetricsForSuccessfulExecution() {
        assertThat(success.getCount()).isZero();

        // Successful execution
        when(delegate.execute(any())).thenReturn(Futures.immediateFuture(null));
        timingChannel.execute(Request.builder().build());

        assertThat(success.getCount()).isOne();
        assertThat(responseErrors.getCount()).isZero();
    }

    @Test
    public void addsMetricsForUnsuccessfulExecution_runtimeException() {
        assertThat(success.getCount()).isZero();
        assertThat(responseErrors.getCount()).isZero();

        // Unsuccessful execution with IOException
        when(delegate.execute(any())).thenReturn(Futures.immediateFailedFuture(new RuntimeException()));
        timingChannel.execute(Request.builder().build());

        assertThat(success.getCount()).isZero();
        assertThat(responseErrors.getCount()).isZero();
    }

    @Test
    public void addsMetricsForUnsuccessfulExecution_ioException() {
        assertThat(success.getCount()).isZero();
        assertThat(responseErrors.getCount()).isZero();

        // Unsuccessful execution with IOException
        when(delegate.execute(any())).thenReturn(Futures.immediateFailedFuture(new IOException()));

        timingChannel.execute(Request.builder().build());
        assertThat(success.getCount()).describedAs("timer.count").isZero();
        assertThat(responseErrors.getCount()).describedAs("responseErrors").isOne();
    }
}
