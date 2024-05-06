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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.Optional;
import java.util.function.Function;

/** When we have zero URIs, no request can get out the door. */
final class ZeroUriNodeSelectionChannel implements LimitedChannel {
    private final Function<Endpoint, Throwable> throwableFactory;

    ZeroUriNodeSelectionChannel(String channelName) {
        this();
        Preconditions.checkNotNull(channelName, "Channel name is required");
    }

    ZeroUriNodeSelectionChannel(Function<Endpoint, Throwable> throwableFactory) {
        this.throwableFactory = Preconditions.checkNotNull(throwableFactory, "Throwable supplier is required");
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(
            Endpoint endpoint, Request _request, LimitEnforcement _limitEnforcement) {
        return Optional.of(Futures.immediateFailedFuture(throwableFactory.apply(endpoint)));
    }
}
