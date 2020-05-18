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

import com.codahale.metrics.Counter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;

final class ActiveRequestInstrumentationChannel implements Channel2 {

    private final String channelName;
    private final String stage;
    private final DialogueClientMetrics metrics;
    private final Channel delegate;

    ActiveRequestInstrumentationChannel(
            Channel delegate,
            String channelName,
            final @CompileTimeConstant String stage,
            TaggedMetricRegistry metrics) {
        // The delegate must never be allowed to throw, otherwise the counter may be incremented without
        // being decremented.
        this.delegate = new NeverThrowChannel(delegate);
        this.channelName = channelName;
        this.stage = stage;
        this.metrics = DialogueClientMetrics.of(metrics);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Counter counter = metrics.requestActive()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .stage(stage)
                .build();
        counter.inc();
        return DialogueFutures.addDirectListener(delegate.execute(endpoint, request), counter::dec);
    }

    @Override
    public String toString() {
        return "ActiveRequestInstrumentationChannel{stage=" + stage + ", delegate=" + delegate + '}';
    }
}
