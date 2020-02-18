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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** See also {@link RoundRobinChannel}. */
final class PinUntilErrorChannel implements LimitedChannel {
    private static final Logger log = LoggerFactory.getLogger(PinUntilErrorChannel.class);

    // TODO(dfox): could we make this endpoint-specific?
    private final AtomicInteger currentHost = new AtomicInteger(0); // increases forever (use toIndex)
    private final ImmutableList<LimitedChannel> delegates;

    PinUntilErrorChannel(List<LimitedChannel> delegates) {
        this.delegates = ImmutableList.copyOf(delegates);
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        if (delegates.isEmpty()) {
            log.debug("Rejecting request due to no delegates");
            return Optional.empty();
        }

        int currentIndex = toIndex(currentHost.get());
        LimitedChannel channel = delegates.get(currentIndex);

        Optional<ListenableFuture<Response>> maybeFuture = channel.maybeExecute(endpoint, request);
        if (!maybeFuture.isPresent()) {
            OptionalInt next = incrementCurrentHost(currentIndex);
            log.debug(
                    "Current channel rejected request, switching to next channel",
                    SafeArg.of("current", currentIndex),
                    SafeArg.of("next", next));
            return Optional.empty(); // if the caller retries immediately, we'll get the next host
        }

        ListenableFuture<Response> future = maybeFuture.get();

        DialogueFutures.addDirectCallback(future, new FutureCallback<Response>() {
            @Override
            public void onSuccess(Response response) {
                if (response.code() / 100 < 2) {
                    // we consider all the 1xx and 2xx codes successful, so want to remain pinned to this channel
                    return;
                }

                // TODO(dfox): handle 308 See Other somehow (don't currently know host -> channel mapping)

                OptionalInt next = incrementCurrentHost(currentIndex);
                log.debug(
                        "Received error status code, switching to next channel",
                        SafeArg.of("status", response.code()),
                        SafeArg.of("current", currentIndex),
                        SafeArg.of("next", next));
            }

            @Override
            public void onFailure(Throwable throwable) {
                OptionalInt next = incrementCurrentHost(currentIndex);
                log.debug(
                        "Received throwable, switching to next channel",
                        SafeArg.of("current", currentIndex),
                        SafeArg.of("next", next),
                        throwable);
            }
        });

        return Optional.of(future);
    }

    /**
     * If we have some reason to think the currentIndex is bad, we want to move to the next host. This is done with a
     * compareAndSet to ensure that
     */
    private OptionalInt incrementCurrentHost(int currentIndex) {
        int next = toIndex(currentIndex + 1);
        boolean saved = currentHost.compareAndSet(currentIndex, next);
        return saved ? OptionalInt.of(next) : OptionalInt.empty(); // we've moved on already
    }

    private int toIndex(int value) {
        return value % delegates.size();
    }
}
