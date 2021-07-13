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

import com.palantir.dialogue.RequestAttachmentKey;
import com.palantir.logsafe.Preconditions;
import org.immutables.value.Value;

public interface RoutingAttachments {

    /** Allows for overriding the {@link QueueStrategy}. */
    RequestAttachmentKey<QueueStrategy> QUEUE_STRATEGY_KEY = RequestAttachmentKey.create(QueueStrategy.class);

    /** When present, {@link #EXECUTED_ON_HOST_ID_RESPONSE_ATTACHMENT_KEY} will be set. */
    RequestAttachmentKey<Boolean> ATTACH_HOST_ID = RequestAttachmentKey.create(Boolean.class);

    /**
     * This request attachment specifies that a request should be executed on a specific host.
     */
    RequestAttachmentKey<HostId> EXECUTE_ON_HOST_ID_KEY = RequestAttachmentKey.create(HostId.class);

    /**
     * If {@link #ATTACH_HOST_ID} is requested, this attachment will be present on the response to indicate the host
     * that executed the request.
     */
    RequestAttachmentKey<HostId> EXECUTED_ON_HOST_ID_RESPONSE_ATTACHMENT_KEY =
            RequestAttachmentKey.create(HostId.class);

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
