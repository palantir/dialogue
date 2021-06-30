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

package com.palantir.dialogue;

import com.palantir.logsafe.Preconditions;
import java.util.UUID;
import org.immutables.value.Value;

public interface RoutingAttachments {

    RequestAttachmentKey<RoutingKey> ROUTING_KEY = RequestAttachmentKey.create(RoutingKey.class);
    RequestAttachmentKey<Boolean> ATTACH_HOST_ID = RequestAttachmentKey.create(Boolean.class);
    RequestAttachmentKey<HostId> HOST_KEY = RequestAttachmentKey.create(HostId.class);

    @Value.Immutable
    interface RoutingKey {
        @Value.Parameter
        UUID value();

        @Value.Parameter
        HostId hostId();

        static RoutingKey create(HostId hostId) {
            return ImmutableRoutingKey.of(UUID.randomUUID(), hostId);
        }
    }

    @Value.Immutable(intern = true)
    interface HostId {
        @Value.Parameter
        int value();

        static HostId of(int hostId) {
            return ImmutableHostId.of(hostId);
        }

        @Value.Check
        default void check() {
            Preconditions.checkArgument(value() >= 0, "Host id >= 0");
        }
    }
}
