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

package com.palantir.dialogue.hc5;

import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import java.io.IOException;
import java.util.List;

/**
 * A request that failed before any of its socket writes completed. It is an {@link IOException} but not a
 * {@link java.net.SocketException}, so it is retried even when the cause is {@code ETIMEDOUT}.
 */
final class SafeRequestNotSentException extends IOException implements SafeLoggable {
    private static final String MESSAGE = "Connection failed before the first write of the request completed";

    SafeRequestNotSentException(IOException cause) {
        super(MESSAGE, cause);
    }

    @Override
    public String getLogMessage() {
        return MESSAGE;
    }

    @Override
    public List<Arg<?>> getArgs() {
        return List.of();
    }
}
