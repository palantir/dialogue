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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round robins requests across many channels, attempting to choose a channel that has some available capacity.
 */
final class RoundRobinChannel implements Channel {

    private final AtomicInteger currentHost = new AtomicInteger(0);
    private final ImmutableList<LimitedChannel> delegates;

    RoundRobinChannel(List<LimitedChannel> delegates) {
        this.delegates = ImmutableList.copyOf(delegates);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        if (delegates.isEmpty()) {
            return Futures.immediateFailedFuture(new SafeIllegalStateException("No nodes are available"));
        }

        int host = currentHost.getAndUpdate(value -> toIndex(value + 1));

        for (int i = 0; i < delegates.size(); i++) {
            LimitedChannel channel = delegates.get(toIndex(host + i));
            Optional<ListenableFuture<Response>> maybeCall = channel.maybeExecute(endpoint, request);
            if (maybeCall.isPresent()) {
                return maybeCall.get();
            }
        }

        return delegates.get(toIndex(host)).execute(endpoint, request);
    }

    private int toIndex(int value) {
        return value % delegates.size();
    }
}
