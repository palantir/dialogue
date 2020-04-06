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

import com.codahale.metrics.Counter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestEndpoint;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import org.junit.jupiter.api.Test;

final class ActiveRequestInstrumentationChannelTest {
    private static final Endpoint ENDPOINT = TestEndpoint.INSTANCE;

    @Test
    public void testActiveRequests() {
        SettableFuture<Response> future = SettableFuture.create();
        Channel stub = (_endpoint, _request) -> future;
        DialogueClientMetrics metrics = DialogueClientMetrics.of(new DefaultTaggedMetricRegistry());
        ActiveRequestInstrumentationChannel instrumented =
                new ActiveRequestInstrumentationChannel(stub, "my-channel", "stage", metrics);
        ListenableFuture<Response> result =
                instrumented.execute(ENDPOINT, Request.builder().build());
        assertThat(result).isNotDone();
        Counter counter = metrics.requestActive()
                .channelName("my-channel")
                .serviceName(ENDPOINT.serviceName())
                .stage("stage")
                .build();
        assertThat(counter.getCount()).describedAs("metric").isOne();
        future.cancel(false);
        assertThat(counter.getCount()).describedAs("metric").isZero();
    }
}
