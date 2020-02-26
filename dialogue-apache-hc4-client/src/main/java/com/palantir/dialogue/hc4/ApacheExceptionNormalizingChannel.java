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

package com.palantir.dialogue.hc4;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.conn.ConnectTimeoutException;

/**
 * Replaces apache httpclient {@link ConnectTimeoutException} exceptions with standard
 * {@link SocketTimeoutException}.
 */
final class ApacheExceptionNormalizingChannel implements BlockingChannel {

    private final BlockingChannel delegate;

    ApacheExceptionNormalizingChannel(BlockingChannel delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response execute(Endpoint endpoint, Request request) throws IOException {
        try {
            return delegate.execute(endpoint, request);
        } catch (ConnectTimeoutException e) {
            // Do not modify the message, it matches the message used by java sockets on timeout
            // to avoid multiple codepaths.
            // In the future we should add client exceptions to conjure-java-runtime-api to differentiate
            // types of failures.
            throw new SafeSocketTimeoutException("connect timed out", e);
        }
    }

    private static final class SafeSocketTimeoutException extends SocketTimeoutException implements SafeLoggable {

        private final String message;

        SafeSocketTimeoutException(@CompileTimeConstant String message, @Nullable Throwable cause) {
            super(message);
            this.message = message;
            initCause(cause);
        }

        @Override
        public String getLogMessage() {
            return message;
        }

        @Override
        public List<Arg<?>> getArgs() {
            return ImmutableList.of();
        }
    }
}
