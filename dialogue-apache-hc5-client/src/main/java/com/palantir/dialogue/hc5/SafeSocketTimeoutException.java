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

package com.palantir.dialogue.hc5;

import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.logsafe.exceptions.SafeExceptions;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** An exception describing a socket timeout.*/
final class SafeSocketTimeoutException extends SocketTimeoutException implements SafeLoggable {

    private final String message;
    private final List<Arg<?>> arguments;

    SafeSocketTimeoutException(@CompileTimeConstant String message, IOException cause) {
        super(message);
        initCause(cause);
        this.message = message;
        this.arguments = Collections.emptyList();
    }

    SafeSocketTimeoutException(@CompileTimeConstant String message, IOException cause, Arg<?>... args) {
        super(SafeExceptions.renderMessage(message, args));
        initCause(cause);
        this.message = message;
        this.arguments = Collections.unmodifiableList(Arrays.asList(args));
    }

    @Override
    public String getLogMessage() {
        return message;
    }

    @Override
    public List<Arg<?>> getArgs() {
        return arguments;
    }
}
