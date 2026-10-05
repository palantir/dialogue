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

package com.palantir.dialogue;

/** Immutable diagnostic metadata attached to a failure as a suppressed exception. */
public final class RetriesExhaustedException extends RuntimeException {
    /** Internal attachment shared by the retry channel and error decoder. */
    public static final ResponseAttachmentKey<Boolean> RESPONSE_ATTACHMENT_KEY =
            ResponseAttachmentKey.create(Boolean.class);

    @SuppressWarnings("StaticAssignmentOfThrowable") // This marker is immutable and never captures a stack trace.
    public static final RetriesExhaustedException INSTANCE = new RetriesExhaustedException();

    private RetriesExhaustedException() {
        super("Dialogue retries exhausted", null, false, false);
    }
}
