/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.dialogue.Response;

/**
 * Extracts the exception for an error {@link Response}, which {@link #decode} returns for the caller to throw: a
 * {@link QosException} for 429 and 503 responses, a {@link RemoteException} for errors it can read (Conjure errors,
 * and legacy errors with a message instead of an error name), and otherwise an {@link UnknownRemoteException}, for
 * example when the body isn't JSON. A 308 with a Location header returns an {@link UnknownRemoteException} caused by
 * a {@link QosException.RetryOther}.
 *
 * <p>This is the public entry point to the same decoding that this package's deserializers use, without any
 * endpoint-specific errors. The implementation is in {@code ExceptionDeserializingErrorDecoder}.
 */
public enum ErrorDecoder {
    INSTANCE;

    public boolean isError(Response response) {
        return ExceptionDeserializingErrorDecoder.isError(response);
    }

    public RuntimeException decode(Response response) {
        return ExceptionDeserializingErrorDecoder.decode(response, ImmutableMap.of());
    }
}
