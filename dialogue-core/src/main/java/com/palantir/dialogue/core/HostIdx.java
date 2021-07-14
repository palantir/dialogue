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

import com.palantir.dialogue.DialogueImmutablesStyle;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import org.immutables.value.Value;

/**
 * Stable and unique index that identifies a host in {@link HostAndLimitedChannels}.
 *
 * <p>Should be used whenever referring to hosts, instead of using URIs, as those are unsafe.</p>
 */
@Value.Immutable(builder = false)
@DialogueImmutablesStyle
interface HostIdx {

    @Value.Parameter
    int index();

    @Value.Derived
    @Value.Auxiliary
    default SafeArg<Integer> safeArg() {
        return SafeArg.of("hostIndex", index());
    }

    static HostIdx of(int index) {
        Preconditions.checkArgument(index >= 0, "index must be >= 0");
        return ImmutableHostIdx.of(index);
    }
}
