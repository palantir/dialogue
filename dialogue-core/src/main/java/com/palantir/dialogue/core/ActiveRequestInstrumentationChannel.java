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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

final class ActiveRequestInstrumentationChannel implements EndpointChannel {
    private final Counter counter;
    private final EndpointChannel proceed;

    private ActiveRequestInstrumentationChannel(Counter counter, EndpointChannel proceed) {
        this.counter = counter;
        this.proceed = proceed;
    }

    static EndpointChannel create(
            Config cf, EndpointChannel delegate, Endpoint endpoint, @CompileTimeConstant String stage) {
        Counter counter = DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry())
                .requestActive()
                .channelName(cf.channelName())
                .serviceName(endpoint.serviceName())
                .stage(stage)
                .build();

        return new ActiveRequestInstrumentationChannel(counter, delegate);
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        counter.inc();
        return DialogueFutures.addDirectListener(proceed.execute(request), counter::dec);
    }

    @Override
    public String toString() {
        return "ActiveRequestInstrumentationEndpointChannel{" + proceed + '}';
    }
}
