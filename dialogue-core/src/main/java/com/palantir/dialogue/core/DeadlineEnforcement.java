/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestAttachmentKey;
import com.palantir.dialogue.Response;
import java.util.Optional;

/**
 * A utility class for constructing a {@link DialogueChannelFactory} which optionally sets a deadline enforcement strategy.
 * <p>
 * This class is intended to be internal API, but is public so that classes within dialogue-clients can create a factory
 * that sets an enforcement strategy when constructing clients.
 */
public final class DeadlineEnforcement {

    private DeadlineEnforcement() {}

    /**
     * Create a {@link DialogueChannelFactory} wrapping an existing factory and optionally controls deadline enforcement.
     * @param enforcement an {@link Optional} boolean indicating the deadline enforcement strategy. If absent, the
     * default strategy is used. If false, deadline enforcement will be disabled; if true, enforcement will be
     * enabled
     * @param delegate a {@link DialogueChannelFactory} to wrap, such that channels created by this factory will be
     * wrapped in a channel which sets the deadline enforcement strategy first
     * @return a {@link DialogueChannelFactory} conforming to the above rules
     */
    // public so that this factory method can be called from dialogue-clients
    public static DialogueChannelFactory createDialogueChannelFactoryWithDeadlineEnforcement(
            Optional<Boolean> enforcement, DialogueChannelFactory delegate) {
        if (enforcement.isEmpty()) {
            return delegate;
        }
        return args -> {
            Channel delegateChannel = delegate.create(args);
            return new DeadlineEnforcementChannel(delegateChannel, enforcement.get());
        };
    }

    private static final RequestAttachmentKey<Boolean> ATTACHMENT_KEY = RequestAttachmentKey.create(Boolean.class);

    // this method is called by DeadlineAdvertisementChannel to read the enforcement strategy for the request
    static Optional<Boolean> getDeadlineEnforcement(Request request) {
        return Optional.ofNullable(request.attachments().getOrDefault(ATTACHMENT_KEY, null));
    }

    private static final class DeadlineEnforcementChannel implements Channel {
        private final Channel delegate;
        private final boolean enforcement;

        private DeadlineEnforcementChannel(Channel delegate, boolean enforcement) {
            this.delegate = delegate;
            this.enforcement = enforcement;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            request.attachments().put(ATTACHMENT_KEY, enforcement);
            return delegate.execute(endpoint, request);
        }
    }
}
