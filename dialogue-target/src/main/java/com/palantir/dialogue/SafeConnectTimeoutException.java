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

import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.logsafe.exceptions.SafeExceptions;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An exception describing a connect timeout. Note that this importantly implements {@link IOException} but
 * not {@link SocketTimeoutException} so that it can be safely retried. Connect timeouts are safe
 * to retry because requests are not sent during connection establishment.
 * <p>
 * This type is public so that blocking clients can report connect timeouts as a {@link DialogueConnectException}.
 */
public final class SafeConnectTimeoutException extends IOException implements SafeLoggable {
    private static final String MESSAGE = "Connect timed out";

    private final List<Arg<?>> arguments;

    public SafeConnectTimeoutException(SocketTimeoutException cause, Arg<?>... args) {
        super(SafeExceptions.renderMessage(MESSAGE, args), cause);
        this.arguments = Collections.unmodifiableList(Arrays.asList(args));
    }

    @Override
    public String getLogMessage() {
        return MESSAGE;
    }

    @Override
    public List<Arg<?>> getArgs() {
        return arguments;
    }
}
