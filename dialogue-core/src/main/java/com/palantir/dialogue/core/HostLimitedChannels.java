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

import com.google.common.collect.ImmutableList;
import java.util.IdentityHashMap;

final class HostLimitedChannels {

    private final ImmutableList<HostLimitedChannel> channels;
    private final IdentityHashMap<HostIdx, HostLimitedChannel> byHostIdx = null;
    private final IdentityHashMap<HostLimitedChannel, HostIdx> byHostLimitedChannel = null;

    HostLimitedChannels(ImmutableList<HostLimitedChannel> channels) {
        this.channels = channels;
        // hostIdxs = new IdentityHashMap<>();
        // for (int i = 0; i < channels.size(); i++) {
        //     hostIdxs.put(channels.get(i), HostIdx.of(i));
        // }
    }

    HostIdx getIdx(HostLimitedChannel limitedChannel) {
        // HostIdx idx = hostIdxs.get(limitedChannel);
        // Preconditions.checkNotNull(idx, "idx");
        // return idx;
        return null;
    }

    ImmutableList<HostLimitedChannel> getChannels() {
        return channels;
    }

    boolean isValid(HostLimitedChannel hostLimitedChannel) {
        //
        return true;
    }

    HostLimitedChannel getByHostIdx(HostIdx hostIdx) {
        return null;
    }

    static HostLimitedChannels create(ImmutableList<HostLimitedChannel> limitedChannels) {
        return null;
    }
}
