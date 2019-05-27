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

import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("FutureReturnValueIgnored")
public final class InstrumentedChannelTest {

    private TaggedMetricRegistry registry;
    private InstrumentedChannel channel;
    @Mock private Channel delegate;
    @Mock private Endpoint endpoint;

    @Before
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
        channel = new InstrumentedChannel(delegate, registry);
    }

    @Test
    public void addsMetricsForSuccessfulAndUnsuccessfulExecution() throws IOException {
        when(endpoint.serviceName()).thenReturn("my-service");

        MetricName name = MetricName.builder()
                .safeName(InstrumentedChannel.CLIENT_RESPONSE_METRIC_NAME)
                .putSafeTags(InstrumentedChannel.SERVICE_NAME_TAG, endpoint.serviceName())
                .build();
        Timer timer = registry.timer(name);

        assertThat(timer.getCount()).isEqualTo(0);
        when(delegate.execute(any(), any())).thenReturn(Futures.immediateFuture(null));
        channel.execute(endpoint, null);
        assertThat(timer.getCount()).isEqualTo(1);

        when(delegate.execute(any(), any())).thenReturn(Futures.immediateFailedFuture(new RuntimeException()));
        channel.execute(endpoint, null);
        assertThat(timer.getCount()).isEqualTo(2);
    }
}
