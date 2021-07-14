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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;

final class HostAndLimitedChannels {

    private final ImmutableList<HostAndLimitedChannel> channels;
    private final BiMap<HostIdx, HostAndLimitedChannel> lookups;

    HostAndLimitedChannels(ImmutableList<HostAndLimitedChannel> channels) {
        this.channels = channels;
        this.lookups = HashBiMap.create(channels.size());
        channels.forEach(hostLimitedChannel -> lookups.put(hostLimitedChannel.getHostIdx(), hostLimitedChannel));
    }

    ImmutableList<HostAndLimitedChannel> getChannels() {
        return channels;
    }

    boolean isValid(HostAndLimitedChannel hostAndLimitedChannel) {
        return lookups.inverse().containsKey(hostAndLimitedChannel);
    }

    HostAndLimitedChannel getByHostIdx(HostIdx hostIdx) {
        return lookups.get(hostIdx);
    }

    static HostAndLimitedChannels create(ImmutableList<HostAndLimitedChannel> limitedChannels) {
        return new HostAndLimitedChannels(limitedChannels);
    }
}
