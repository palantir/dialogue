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
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Maintains stable {@code HostIdx -> LimitedChannel} association. Channels can be shuffled to obtain different
 * orderings, whilst still maintaining the ability to retrieve channels by their original {@link HostIdx}.
 */
final class HostAndLimitedChannels {

    private final ImmutableList<HostAndLimitedChannel> channels;
    private final ImmutableBiMap<HostIdx, HostAndLimitedChannel> lookups;
    private final ImmutableMap<HostAndLimitedChannel, Integer> byIndex;
    private final ImmutableMap<LimitedChannel, HostAndLimitedChannel> byLimitedChannel;

    private HostAndLimitedChannels(ImmutableList<HostAndLimitedChannel> channels) {
        this(channels, buildLookups(channels), buildByLimitedChannel(channels));
    }

    private HostAndLimitedChannels(
            ImmutableList<HostAndLimitedChannel> channels,
            ImmutableBiMap<HostIdx, HostAndLimitedChannel> lookups,
            ImmutableMap<LimitedChannel, HostAndLimitedChannel> byLimitedChannel) {
        this.channels = channels;
        this.lookups = lookups;
        this.byLimitedChannel = byLimitedChannel;
        this.byIndex = buildByIndex(channels);
    }

    ImmutableList<HostAndLimitedChannel> getChannels() {
        return channels;
    }

    HostAndLimitedChannel getByHostIdx(HostIdx hostIdx) {
        return Preconditions.checkNotNull(lookups.get(hostIdx), "Unknown hostIdx");
    }

    int getCurrentIndex(HostAndLimitedChannel hostAndLimitedChannel) {
        return Preconditions.checkNotNull(byIndex.get(hostAndLimitedChannel), "Unknown hostAndLimitedChannel");
    }

    HostAndLimitedChannel getByLimitedChannel(LimitedChannel limitedChannel) {
        return Preconditions.checkNotNull(byLimitedChannel.get(limitedChannel), "Unknown limitedChannel");
    }

    HostAndLimitedChannels shuffle(Random random) {
        List<HostAndLimitedChannel> mutableList = new ArrayList<>(channels);
        Collections.shuffle(mutableList, random);
        return new HostAndLimitedChannels(ImmutableList.copyOf(mutableList), lookups, byLimitedChannel);
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

    private static ImmutableBiMap<HostIdx, HostAndLimitedChannel> buildLookups(
            ImmutableList<HostAndLimitedChannel> channels) {
        ImmutableBiMap.Builder<HostIdx, HostAndLimitedChannel> lookupsBuilder = ImmutableBiMap.builder();
        channels.forEach(hostLimitedChannel -> lookupsBuilder.put(hostLimitedChannel.getHostIdx(), hostLimitedChannel));
        return lookupsBuilder.build();
    }

    private static ImmutableMap<HostAndLimitedChannel, Integer> buildByIndex(
            ImmutableList<HostAndLimitedChannel> channels) {
        ImmutableMap.Builder<HostAndLimitedChannel, Integer> byIndexBuilder = ImmutableMap.builder();
        for (int i = 0; i < channels.size(); i++) {
            byIndexBuilder.put(channels.get(i), i);
        }
        return byIndexBuilder.build();
    }

    private static ImmutableMap<LimitedChannel, HostAndLimitedChannel> buildByLimitedChannel(
            ImmutableList<HostAndLimitedChannel> channels) {
        ImmutableMap.Builder<LimitedChannel, HostAndLimitedChannel> byLimitedChannel = ImmutableMap.builder();
        for (int i = 0; i < channels.size(); i++) {
            HostAndLimitedChannel hostAndLimitedChannel = channels.get(i);
            byLimitedChannel.put(hostAndLimitedChannel.limitedChannel(), hostAndLimitedChannel);
        }
        return byLimitedChannel.build();
    }
}
