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

import com.palantir.logsafe.Arg;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.logsafe.exceptions.SafeExceptions;
import java.util.List;
import java.util.OptionalLong;

/**
 * Thrown when a request waits in a {@link QueuedChannel} longer than the configured queue timeout.
 */
final class QueueTimeoutException extends RuntimeException implements SafeLoggable {
    private static final String MESSAGE = "Request queued for longer than queue timeout";

    private final List<Arg<?>> args;

    QueueTimeoutException(@Safe String channelName, @Safe OptionalLong queueTimeoutNanos) {
        this(List.of(SafeArg.of("channelName", channelName), SafeArg.of("queueTimeoutNanos", queueTimeoutNanos)));
    }

    private QueueTimeoutException(List<Arg<?>> args) {
        super(SafeExceptions.renderMessage(MESSAGE, args));
        this.args = args;
    }

    @Override
    public String getLogMessage() {
        return MESSAGE;
    }

    @Override
    public List<Arg<?>> getArgs() {
        return args;
    }
}
