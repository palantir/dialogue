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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.time.Duration;
import java.util.Optional;

/**
 * Delegates execution to the given {@link LimitedChannel} until a failure is seen, at which point the channel will
 * return {@link Optional#empty()} until the given {@link Duration} has elapsed.
 */
final class BlacklistingChannel implements LimitedChannel {

    private static final String KEY = "key";
    private static final String VALUE = "value";

    private final LimitedChannel delegate;
    private final Cache<String, String> isBlacklisted;

    BlacklistingChannel(LimitedChannel delegate, Duration duration) {
        this(delegate, duration, Ticker.systemTicker());
    }

    @VisibleForTesting
    BlacklistingChannel(LimitedChannel delegate, Duration duration, Ticker ticker) {
        this.delegate = delegate;
        this.isBlacklisted = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(duration)
                .ticker(ticker)
                .build();
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        if (isBlacklisted.getIfPresent(KEY) != null) {
            return Optional.empty();
        } else {
            return delegate.maybeExecute(endpoint, request)
                    .map(future -> DialogueFutures.addDirectCallback(future, new BlacklistingCallback<>()));
        }
    }

    private class BlacklistingCallback<T> implements FutureCallback<T> {
        @Override
        public void onSuccess(T result) {}

        @Override
        public void onFailure(Throwable throwable) {
            isBlacklisted.put(KEY, VALUE);
        }
    }
}
