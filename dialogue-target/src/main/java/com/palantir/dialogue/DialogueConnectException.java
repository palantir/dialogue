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

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.SafeArg;

/**
 * Dialogue couldn't connect to the server, so the request was never sent. Dialogue retries these before giving up.
 */
public final class DialogueConnectException extends DialogueException {
    private final Reason reason;

    public DialogueConnectException(Reason reason, Throwable cause) {
        super(cause, ImmutableList.of(SafeArg.of("reason", reason)));
        this.reason = reason;
    }

    /** Why the connection couldn't be made. */
    public Reason reason() {
        return reason;
    }

    public enum Reason {
        /** The host name couldn't be resolved. */
        DNS,
        /** The connection was refused, usually because nothing is listening on the port. */
        CONNECTION_REFUSED,
        /** The connection wasn't established within the connect timeout. */
        CONNECT_TIMEOUT,
        /** The TLS handshake failed, for example because the server's certificate isn't trusted. */
        TLS
    }
}
