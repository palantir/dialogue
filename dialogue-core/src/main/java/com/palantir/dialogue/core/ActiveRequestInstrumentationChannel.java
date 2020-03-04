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
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;

final class ActiveRequestInstrumentationChannel implements Channel {

    private final String stage;
    private final Channel delegate;
    private final Counter counter;
    private final Runnable completionListener;

    ActiveRequestInstrumentationChannel(
            Channel delegate, @CompileTimeConstant String stage, DialogueClientMetrics metrics) {
        this.stage = stage;
        this.delegate = new NeverThrowChannel(delegate);
        this.counter = metrics.requestsActive(stage);
        this.completionListener = counter::dec;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        counter.inc();
        ListenableFuture<Response> result = delegate.execute(endpoint, request);
        result.addListener(completionListener, MoreExecutors.directExecutor());
        return result;
    }

    @Override
    public String toString() {
        return "ActiveRequestInstrumentationChannel{" + "delegate=" + delegate + ", stage=" + stage + '}';
    }
}
