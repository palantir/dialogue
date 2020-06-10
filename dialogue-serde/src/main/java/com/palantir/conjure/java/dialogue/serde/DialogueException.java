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

package com.palantir.conjure.java.dialogue.serde;

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import java.util.List;

/** Internal marker type for failure legibility, this type is not meant to be handled directly. */
final class DialogueException extends RuntimeException implements SafeLoggable {
    private static final String MESSAGE = "Dialogue transport failure";

    DialogueException(Throwable cause) {
        super(copyWhitelistedSafeMessages(cause), cause);
    }

    @Override
    public String getLogMessage() {
        return MESSAGE;
    }

    @Override
    public List<Arg<?>> getArgs() {
        return ImmutableList.of();
    }

    private static String copyWhitelistedSafeMessages(Throwable cause) {
        if (cause == null) {
            return MESSAGE;
        }

        String causeMessage = cause.getMessage();
        if (causeMessage == null) {
            return MESSAGE;
        }

        switch (causeMessage) {
            case "Connection reset":
            case "Connection reset by peer":
            case "Broken pipe (Write failed)":
            case "Remote host terminated the handshake":
            case "SSL peer shut down incorrectly":
                return causeMessage;
            default:
                return MESSAGE;
        }
    }
}
