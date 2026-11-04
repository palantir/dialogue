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

import java.net.SocketException;
import java.net.SocketTimeoutException;
import org.jspecify.annotations.Nullable;

/**
 * The request was sent but no response arrived within the client's read timeout, so the server may have processed
 * it. Dialogue doesn't retry these by default.
 */
public final class DialogueResponseTimeoutException extends DialogueException implements ClientTimeout {

    public DialogueResponseTimeoutException(Throwable cause) {
        super(cause);
    }

    /**
     * Returns whether Dialogue treats {@code failure} as a response timeout: a {@link SocketTimeoutException} that
     * isn't a connect timeout, or a connection that timed out at the TCP level after it was established. These are
     * the timeouts Dialogue doesn't retry by default.
     */
    public static boolean isResponseTimeout(@Nullable Throwable failure) {
        if (failure instanceof SocketTimeoutException socketTimeout) {
            String message = socketTimeout.getMessage();
            // String matches CJR RemotingOkHttpCall.shouldRetry
            return message == null || !message.contains("connect timed out");
        }
        return isEtimedoutException(failure);
    }

    /**
     * Checks if the input is the result of a TCP level {@code ETIMEDOUT} error. This means that SYN segment did
     * not result in an ACK from the remote server, most likely because connections have been severed in an
     * exceptional/unclean way (for example, a network cable removed may have been removed).
     * <p>
     * This can be thrown when we attempt to read from existing connection sockets, which occurs after we've
     * sent a request and the remote server has begun processing.
     */
    private static boolean isEtimedoutException(@Nullable Throwable failure) {
        // https://github.com/openjdk/jdk/blob/32eb5290c207d5fda398ee09b354b8cf55b89e0c/src/hotspot/share/runtime/os.cpp#L1658
        return failure != null
                && SocketException.class.equals(failure.getClass())
                && "Connection timed out".equals(failure.getMessage());
    }
}
