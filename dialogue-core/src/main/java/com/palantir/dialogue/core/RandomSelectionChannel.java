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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;

/**
 * Randomly selects a channel for a given request, attempting to choose a channel that has some available capacity.
 * @deprecated in favour of {@link PreferLowestRememberFailures}
 */
@Deprecated
final class RandomSelectionChannel implements LimitedChannel {

    private final ImmutableList<LimitedChannel> delegates;
    private final Random random;

    RandomSelectionChannel(List<LimitedChannel> delegates, Random random) {
        this.delegates = ImmutableList.copyOf(delegates);
        this.random = random;
        Preconditions.checkArgument(!this.delegates.isEmpty(), "Delegates must not be empty");
    }

    @Override
    public Optional<ListenableFuture<Response>> maybeExecute(Endpoint endpoint, Request request) {
        int elements = delegates.size();
        // Defer collection creation in the hot path, there's no need to create objects for tracking
        // if the first randomly selected host is successful.
        BitSet visitedChannels = null;
        while (elements > 0) {
            int host = random.nextInt(elements);
            LimitedChannel channel = delegates.get(toIndex(visitedChannels, host));
            Optional<ListenableFuture<Response>> maybeCall = channel.maybeExecute(endpoint, request);
            if (maybeCall.isPresent()) {
                return maybeCall;
            }
            if (visitedChannels == null) {
                visitedChannels = new BitSet(elements);
            }
            // Mark 'host' index visited, reduce the available channels, and try again.
            visitedChannels.set(host);
            --elements;
        }
        return Optional.empty();
    }

    /**
     * Finds the Nth unused channel index a bit-set representing channel indexes that have been attempted and resulted
     * in limited results. The resulting index can be used to look up a channel in the master list which includes
     * channels that have already been attempted.
     */
    @VisibleForTesting
    static int toIndex(@Nullable BitSet visited, int index) {
        if (visited == null || visited.isEmpty()) {
            return index;
        }
        return nthFalse(visited, index);
    }

    /**
     * Finds the Nth false in the provided bitset, for example <code>nthFalse([false], 0)</code> returns zero, while
     * <code>nthFalse([true, false], 0)</code> returns one.
     */
    private static int nthFalse(BitSet bitSet, int num) {
        int remaining = num;
        for (int currentIndex = 0; true; currentIndex++) {
            if (!bitSet.get(currentIndex)) {
                if (--remaining < 0) {
                    return currentIndex;
                }
            }
        }
    }
}
