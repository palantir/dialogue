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
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The contract of {@link LimitedChannel} requires that the {@link LimitedChannel#maybeExecute} method never throws.
 * This is a defensive backstop so that callers can rely on this invariant.
 */
final class NeverThrowLimitedChannel implements LimitedChannel {

    private static final Logger log = LoggerFactory.getLogger(NeverThrowLimitedChannel.class);
    private final LimitedChannel delegate;

    NeverThrowLimitedChannel(LimitedChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        try {
            return delegate.maybeExecute(endpoint, request);
        } catch (RuntimeException | Error e) {
            log.error("Dialogue channels should never throw. This may be a bug in the channel implementation", e);
            return Optional.of(Futures.immediateFailedFuture(e));
        }
    }

    @Override
    public String toString() {
        return "NeverThrowLimitedChannel{" + delegate + '}';
    }
}
