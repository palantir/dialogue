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
import com.palantir.logsafe.Preconditions;

final class HostLimitedChannels {

    private final ImmutableList<HostLimitedChannel> channels;
    private final BiMap<HostIdx, HostLimitedChannel> lookups;

    HostLimitedChannels(ImmutableList<HostLimitedChannel> channels) {
        this.channels = channels;
        this.lookups = HashBiMap.create(channels.size());
        channels.forEach(hostLimitedChannel -> lookups.put(hostLimitedChannel.getHostIdx(), hostLimitedChannel));
    }

    HostIdx getIdx(HostLimitedChannel limitedChannel) {
        return Preconditions.checkNotNull(lookups.inverse().get(limitedChannel));
    }

    ImmutableList<HostLimitedChannel> getChannels() {
        return channels;
    }

    boolean isValid(HostLimitedChannel hostLimitedChannel) {
        return lookups.inverse().containsKey(hostLimitedChannel);
    }

    HostLimitedChannel getByHostIdx(HostIdx hostIdx) {
        return lookups.get(hostIdx);
    }

    static HostLimitedChannels create(ImmutableList<HostLimitedChannel> limitedChannels) {
        return new HostLimitedChannels(limitedChannels);
    }
}
