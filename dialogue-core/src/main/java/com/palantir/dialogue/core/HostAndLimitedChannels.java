/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

final class HostAndLimitedChannels {

    private final ImmutableList<HostAndLimitedChannel> channels;
    private final ImmutableBiMap<HostIdx, HostAndLimitedChannel> lookups;

    private HostAndLimitedChannels(ImmutableList<HostAndLimitedChannel> channels) {
        this.channels = channels;
        ImmutableBiMap.Builder<HostIdx, HostAndLimitedChannel> lookupsBuilder = ImmutableBiMap.builder();
        channels.forEach(hostLimitedChannel -> lookupsBuilder.put(hostLimitedChannel.getHostIdx(), hostLimitedChannel));
        lookups = lookupsBuilder.build();
    }

    ImmutableList<HostAndLimitedChannel> getUnorderedChannels() {
        return channels;
    }

    HostAndLimitedChannel getByUnordered(int index) {
        return channels.get(index);
    }

    HostAndLimitedChannel getByHostIdx(HostIdx hostIdx) {
        return Preconditions.checkNotNull(lookups.get(hostIdx), "Unknown hostIdx");
    }

    int unorderedIndexOf(HostAndLimitedChannel hostAndLimitedChannel) {
        return channels.indexOf(hostAndLimitedChannel);
    }

    HostAndLimitedChannels shuffle(Random random) {
        List<HostAndLimitedChannel> mutableList = new ArrayList<>(channels);
        Collections.shuffle(mutableList, random);
        return new HostAndLimitedChannels(ImmutableList.copyOf(mutableList));
    }

    static HostAndLimitedChannels createAndAssignHostIdx(ImmutableList<LimitedChannel> limitedChannels) {
        ImmutableList<HostAndLimitedChannel> channels = IntStream.range(0, limitedChannels.size())
                .mapToObj(HostIdx::of)
                .map(hostIdx -> HostAndLimitedChannel.builder()
                        .hostIdx(hostIdx)
                        .limitedChannel(limitedChannels.get(hostIdx.index()))
                        .build())
                .collect(ImmutableList.toImmutableList());
        return new HostAndLimitedChannels(channels);
    }

    @Override
    public String toString() {
        return channels.toString();
    }
}
