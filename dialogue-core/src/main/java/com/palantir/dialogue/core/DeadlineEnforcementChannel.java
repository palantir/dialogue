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

package com.palantir.dialogue.core;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.deadlines.Deadlines;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestAttachmentKey;
import com.palantir.dialogue.Response;

/**
 * Captures the caller thread's deadline enforcement override before requests can be queued or retried.
 * <p>
 * {@link Deadlines#withEnforcementDisabled} is thread-local, so it is only visible while the calling thread is
 * inside the scope. Later attempts run on queue and retry threads, which restore the trace but not the override, so
 * it is recorded here and applied by {@link DeadlineAdvertisementChannel} on every attempt.
 */
final class DeadlineEnforcementChannel implements EndpointChannel {

    static final RequestAttachmentKey<Boolean> ENFORCEMENT_DISABLED = RequestAttachmentKey.create(Boolean.class);

    private final EndpointChannel delegate;

    DeadlineEnforcementChannel(EndpointChannel delegate) {
        this.delegate = delegate;
    }

    static boolean isEnforcementDisabled(Request request) {
        return Boolean.TRUE.equals(request.attachments().getOrDefault(ENFORCEMENT_DISABLED, false));
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        request.attachments().put(ENFORCEMENT_DISABLED, Deadlines.isEnforcementDisabled());
        return delegate.execute(request);
    }
}
