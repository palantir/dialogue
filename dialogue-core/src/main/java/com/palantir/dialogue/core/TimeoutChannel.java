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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

final class TimeoutChannel implements Channel {

    private final Channel delegate;
    private final Duration timeout;
    private final ScheduledExecutorService scheduler;

    TimeoutChannel(Channel delegate, Duration timeout, ScheduledExecutorService scheduler) {
        this.delegate = delegate;
        this.timeout = timeout;
        this.scheduler = scheduler;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        ListenableFuture<Response> result = delegate.execute(endpoint, request);
        return Futures.withTimeout(result, timeout, scheduler);
    }
}
