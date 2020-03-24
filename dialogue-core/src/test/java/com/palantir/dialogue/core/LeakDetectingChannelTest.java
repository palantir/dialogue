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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.Futures;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeakDetectingChannelTest {
    private static final String CHANNEL = "channel";
    private static final String SERVICE = "service";
    private static final String ENDPOINT = "endpoint";

    private DialogueClientMetrics metrics;

    @Mock
    private Endpoint mockEndpoint;

    @Mock
    private Response response;

    @BeforeEach
    void beforeEach() {
        metrics = DialogueClientMetrics.of(new DefaultTaggedMetricRegistry());
        lenient().when(mockEndpoint.serviceName()).thenReturn(SERVICE);
        lenient().when(mockEndpoint.endpointName()).thenReturn(ENDPOINT);
        lenient().when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored") // intentional leak
    void testLeakMetric() throws Exception {
        Channel delegate = (endpoint, request) -> Futures.immediateFuture(response);
        LeakDetectingChannel detector = new LeakDetectingChannel(delegate, CHANNEL, metrics);
        // Result is intentionally ignored to cause a leak
        detector.execute(mockEndpoint, Request.builder().build());
        Meter leaks = metrics.responseLeak()
                .channelName(CHANNEL)
                .serviceName(SERVICE)
                .endpoint(ENDPOINT)
                .build();
        Awaitility.waitAtMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            System.gc();
            assertThat(leaks.getCount()).isOne();
        });
        verify(response).close();
    }

    @Test
    void testNotLeaked_streamReferenceHeld() throws Exception {
        Channel delegate = (endpoint, request) -> Futures.immediateFuture(response);
        LeakDetectingChannel detector = new LeakDetectingChannel(delegate, CHANNEL, metrics);
        // Result is intentionally ignored to cause a leak
        try (InputStream ignored =
                detector.execute(mockEndpoint, Request.builder().build()).get().body()) {
            Meter leaks = metrics.responseLeak()
                    .channelName(CHANNEL)
                    .serviceName(SERVICE)
                    .endpoint(ENDPOINT)
                    .build();
            // GC and test enough times to be confident no leaks were recorded
            for (int i = 0; i < 100; i++) {
                System.gc();
                Thread.sleep(1);
                assertThat(leaks.getCount()).isZero();
            }
        }
    }
}
