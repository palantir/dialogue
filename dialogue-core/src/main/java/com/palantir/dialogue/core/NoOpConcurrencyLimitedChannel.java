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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;

final class NoOpConcurrencyLimitedChannel implements ConcurrencyLimitedChannel {

    private final LimitedChannel delegate;
    private final AtomicInteger inflight = new AtomicInteger();
    private final Runnable decrement = new Runnable() {
        @Override
        public void run() {
            inflight.decrementAndGet();
        }
    };

    NoOpConcurrencyLimitedChannel(LimitedChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getInflight() {
        return inflight.get();
    }

    @Override
    public OptionalDouble getMax() {
        return OptionalDouble.empty();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        inflight.incrementAndGet();
        Optional<ListenableFuture<Response>> maybe = delegate.maybeExecute(endpoint, request);
        if (maybe.isPresent()) {
            DialogueFutures.addDirectListener(maybe.get(), decrement);
        } else {
            inflight.decrementAndGet();
        }
        return maybe;
    }
}
