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
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class PinUntilError {

    private final AtomicInteger currentPin = new AtomicInteger(0);

    private final int numChannels;
    private final ImmutableList<Integer> shuffledIndexes;
    // TODO(dfox): implement the 10 minute reshuffling
    // TODO(dfox): observability

    private PinUntilError(int numChannels, Random random) {
        Preconditions.checkArgument(
                numChannels >= 2,
                "PinUntilError is pointless if you have zero or 1 channels."
                        + " Use an always throwing channel or just pick the only channel in the list.");
        this.numChannels = numChannels;

        List<Integer> collect = IntStream.range(0, numChannels).boxed().collect(Collectors.toList());
        Collections.shuffle(collect, random);
        this.shuffledIndexes = ImmutableList.copyOf(collect);
    }

    static PinUntilError create(Config cf) {
        return new PinUntilError(cf.clientConf().uris().size(), cf.random());
    }

    /** Based on the shared state in the PinUntilError object, this channel selects one of the provided list. */
    EndpointLimitedChannel createSelectorOverChannels(ImmutableList<EndpointLimitedChannel> singleUriChannels) {
        Preconditions.checkArgument(singleUriChannels.size() == numChannels);
        return new Foo(singleUriChannels);
    }

    final class Foo implements EndpointLimitedChannel {
        private final ImmutableList<EndpointLimitedChannel> singleUriChannels;

        Foo(ImmutableList<EndpointLimitedChannel> singleUriChannels) {
            this.singleUriChannels = singleUriChannels;
        }

        @Override
        public Optional<ListenableFuture<Response>> maybeExecute(Request request) {
            int pin = currentPin.get();
            Integer channelIndex = shuffledIndexes.get(pin);
            EndpointLimitedChannel channel = singleUriChannels.get(channelIndex);

            Optional<ListenableFuture<Response>> maybeResponse = channel.maybeExecute(request);
            if (!maybeResponse.isPresent()) {
                return Optional.empty();
            }

            DialogueFutures.addDirectCallback(maybeResponse.get(), new FutureCallback<Response>() {
                @Override
                public void onSuccess(Response response) {
                    // We specifically don't switch  429 responses to support transactional
                    // workflows where it is important for a large number of requests to all land on the same node,
                    // even if a couple of them get rate limited in the middle.
                    if (Responses.isServerError(response)
                            || (Responses.isQosStatus(response) && !Responses.isTooManyRequests(response))) {
                        incrementHostIfNecessary(pin);
                        // instrumentation.receivedErrorStatus(pin, channel, response, next);
                    } else {
                        // instrumentation.successfulResponse(channelIndex);
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    incrementHostIfNecessary(pin);
                    // instrumentation.receivedThrowable(pin, channel, throwable, next);
                }
            });
            return maybeResponse;
        }
    }

    /**
     * If we have some reason to think the currentIndex is bad, we want to move to the next host. This is done with a
     * compareAndSet to ensure that out of order responses which signal information about a previous host don't kick
     * us off a good one.
     */
    private OptionalInt incrementHostIfNecessary(int pin) {
        int nextIndex = (pin + 1) % numChannels;
        boolean saved = currentPin.compareAndSet(pin, nextIndex);
        return saved ? OptionalInt.of(nextIndex) : OptionalInt.empty(); // we've moved on already
    }
}
