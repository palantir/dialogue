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

package com.palantir.dialogue;

import com.palantir.logsafe.Preconditions;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Immutable behavior overrides which apply to an individual Dialogue call. */
public final class DialogueCallOptions {
    private static final RequestAttachmentKey<DialogueCallOptions> ATTACHMENT_KEY =
            RequestAttachmentKey.create(DialogueCallOptions.class);
    private static final DialogueCallOptions EMPTY = new DialogueCallOptions(null);

    private final @Nullable Boolean deadlineEnforcement;

    private DialogueCallOptions(@Nullable Boolean deadlineEnforcement) {
        this.deadlineEnforcement = deadlineEnforcement;
    }

    public static DialogueCallOptions empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Boolean> deadlineEnforcement() {
        return Optional.ofNullable(deadlineEnforcement);
    }

    public DialogueCallOptions merge(DialogueCallOptions overrides) {
        Preconditions.checkNotNull(overrides, "overrides");
        return new DialogueCallOptions(
                overrides.deadlineEnforcement != null ? overrides.deadlineEnforcement : deadlineEnforcement);
    }

    public void attachTo(Request request) {
        Preconditions.checkNotNull(request, "request");
        DialogueCallOptions existing = request.attachments().getOrDefault(ATTACHMENT_KEY, null);
        request.attachments().put(ATTACHMENT_KEY, existing == null ? this : merge(existing));
    }

    public EndpointChannel decorate(EndpointChannel delegate) {
        Preconditions.checkNotNull(delegate, "delegate");
        return request -> {
            attachTo(request);
            return delegate.execute(request);
        };
    }

    public EndpointChannelFactory decorate(EndpointChannelFactory delegate) {
        Preconditions.checkNotNull(delegate, "delegate");
        return endpoint -> decorate(delegate.endpoint(endpoint));
    }

    public static Optional<Boolean> deadlineEnforcement(Request request) {
        DialogueCallOptions options = request.attachments().getOrDefault(ATTACHMENT_KEY, EMPTY);
        return options == null ? Optional.empty() : options.deadlineEnforcement();
    }

    public static final class Builder {
        private @Nullable Boolean deadlineEnforcement;

        private Builder() {}

        public Builder deadlineEnforcement(boolean value) {
            deadlineEnforcement = value;
            return this;
        }

        public DialogueCallOptions build() {
            return new DialogueCallOptions(deadlineEnforcement);
        }
    }
}
