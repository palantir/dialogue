/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.clients;

import com.google.common.collect.ImmutableSet;
import com.palantir.dialogue.core.DialogueDnsResolver;
import java.net.InetAddress;
import java.util.Optional;

interface DetailedDialogueDnsResolver extends DialogueDnsResolver {

    /**
     * Returns an empty set only when DNS reported that the name does not exist. Other failures, where the name may
     * still be valid, return {@link Optional#empty()}.
     */
    Optional<ImmutableSet<InetAddress>> resolveDetailed(String hostname);

    @Override
    default ImmutableSet<InetAddress> resolve(String hostname) {
        return resolveDetailed(hostname).orElseGet(ImmutableSet::of);
    }
}
