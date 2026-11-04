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

package com.palantir.conjure.java.dialogue.serde;

import com.palantir.dialogue.DialogueConnectException;
import com.palantir.dialogue.DialogueException;
import com.palantir.dialogue.DialogueResponseTimeoutException;
import com.palantir.dialogue.SafeConnectTimeoutException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.jspecify.annotations.Nullable;

/** Wraps a failed blocking call in the most specific {@link DialogueException} that describes the failure. */
final class DialogueExceptions {

    static DialogueException wrap(@Nullable Throwable failure) {
        if (failure == null) {
            return new DialogueException(null);
        }
        if (DialogueResponseTimeoutException.isResponseTimeout(failure)) {
            return new DialogueResponseTimeoutException(failure);
        }
        DialogueConnectException.Reason reason = connectFailureReason(failure);
        if (reason != null) {
            return new DialogueConnectException(reason, failure);
        }
        return new DialogueException(failure);
    }

    private static DialogueConnectException.@Nullable Reason connectFailureReason(Throwable failure) {
        if (failure instanceof UnknownHostException) {
            return DialogueConnectException.Reason.DNS;
        }
        if (failure instanceof ConnectException) {
            return DialogueConnectException.Reason.CONNECTION_REFUSED;
        }
        // Response timeouts were handled first, so a SocketTimeoutException here is a connect timeout
        if (failure instanceof SafeConnectTimeoutException || failure instanceof SocketTimeoutException) {
            return DialogueConnectException.Reason.CONNECT_TIMEOUT;
        }
        if (failure instanceof SSLHandshakeException || failure instanceof SSLPeerUnverifiedException) {
            return DialogueConnectException.Reason.TLS;
        }
        return null;
    }

    private DialogueExceptions() {}
}
