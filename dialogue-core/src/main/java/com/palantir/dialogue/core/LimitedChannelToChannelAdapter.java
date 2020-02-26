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
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.util.function.Supplier;

/** Adapter from {@link LimitedChannel} to {@link Channel} which produces a failed future when results are limited. */
final class LimitedChannelToChannelAdapter implements Channel {

    // Avoid method reference allocations
    @SuppressWarnings("UnnecessaryLambda")
    private static final Supplier<ListenableFuture<Response>> limitedResultSupplier =
            () -> Futures.immediateFailedFuture(new SafeRuntimeException("Failed to make a request"));

    private final LimitedChannel delegate;

    LimitedChannelToChannelAdapter(LimitedChannel delegate) {
        this.delegate = Preconditions.checkNotNull(delegate, "LimitedChannel");
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return delegate.maybeExecute(endpoint, request).orElseGet(limitedResultSupplier);
    }

    @Override
    public String toString() {
        return "LimitedChannelToChannelAdapter{delegate=" + delegate + '}';
    }
}
