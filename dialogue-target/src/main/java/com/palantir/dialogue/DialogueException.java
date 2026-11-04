/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Internal marker type for Dialogue network layer failures where no response is returned.
 * <p>
 * Blocking clients throw a subclass when they can tell what went wrong: {@link DialogueResponseTimeoutException},
 * {@link DialogueConnectException} or {@link DialogueInterruptedException}. Every subclass keeps the same message and
 * the same cause as a plain {@code DialogueException}.
 */
public sealed class DialogueException extends RuntimeException implements SafeLoggable
        permits DialogueResponseTimeoutException, DialogueConnectException, DialogueInterruptedException {
    private static final String MESSAGE = "Network transport failure";

    private final List<Arg<?>> args;

    public DialogueException(@Nullable Throwable cause) {
        this(cause, ImmutableList.of());
    }

    DialogueException(@Nullable Throwable cause, List<Arg<?>> args) {
        super(MESSAGE, cause);
        this.args = args;
    }

    @Override
    public final String getLogMessage() {
        return MESSAGE;
    }

    @Override
    public final List<Arg<?>> getArgs() {
        return args;
    }
}
