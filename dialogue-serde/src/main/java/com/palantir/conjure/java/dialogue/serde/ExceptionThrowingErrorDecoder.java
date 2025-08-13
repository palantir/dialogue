/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.AbstractSerializableError;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.dialogue.ExceptionDeserializerArgs.ErrorExceptionPair;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

final class ExceptionThrowingErrorDecoder<T> extends AbstractErrorDecoder<T> {
    private static final SafeLogger log = SafeLoggerFactory.get(ExceptionThrowingErrorDecoder.class);
    private final Map<String, DeserializerExceptionPair<?>> errorNameToExceptionDeserializerMap;

    ExceptionThrowingErrorDecoder(Map<String, ErrorExceptionPair<?>> errorNameToExceptionTypeMap) {
        this(errorNameToExceptionTypeMap, Optional.empty());
    }

    ExceptionThrowingErrorDecoder(
            Map<String, ErrorExceptionPair<?>> errorNameToExceptionTypeMap, Optional<Encoding> maybeJsonEncoding) {
        this.errorNameToExceptionDeserializerMap = ImmutableMap.copyOf(Maps.transformValues(
                errorNameToExceptionTypeMap,
                errorExceptionPair ->
                        createDeserializerForException(maybeJsonEncoding.orElse(JSON_ENCODING), errorExceptionPair)));
    }

    @Override
    public T decode(Response response) {
        if (log.isDebugEnabled()) {
            log.debug("Received an error response", diagnosticArgs(response));
        }
        try {
            return decodeInternal(response);
        } catch (Exception e) {
            e.addSuppressed(diagnostic(response));
            throw e;
        }
    }

    private T decodeInternal(Response response) {
        Optional<RuntimeException> maybeQosException = checkCode(response, log);
        if (maybeQosException.isPresent()) {
            throw maybeQosException.get();
        }
        int code = response.code();

        byte[] body;
        try {
            body = toByteArray(response.body());
        } catch (NullPointerException | IOException e) {
            UnknownRemoteException exception = new UnknownRemoteException(code, "<unparseable>");
            exception.initCause(e);
            throw exception;
        }

        Optional<String> contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType.isPresent()
                && Encodings.matchesContentType(JSON_ENCODING.getContentType(), contentType.get())) {
            try {
                String errorName = extractErrorName(body);
                if (errorName == null) {
                    throw createRemoteException(body, code);
                }
                DeserializerExceptionPair<?> deserializerExceptionPair =
                        errorNameToExceptionDeserializerMap.get(errorName);
                if (deserializerExceptionPair == null) {
                    throw createRemoteException(body, code);
                }

                // Attempt to deserialize the error using the deserializer for the specific exception type.
                AbstractSerializableError<?> error;
                try {
                    error = deserializerExceptionPair.deserializer().deserialize(new ByteArrayInputStream(body));
                } catch (Exception e) {
                    // If we're unable to deserialize the error as JSON, throw a RemoteException.
                    throw createRemoteException(body, code);
                }

                Type exceptionType = deserializerExceptionPair.exceptionType().getType();
                @SuppressWarnings("unchecked")
                Class<? extends RemoteException> exceptionClass =
                        (Class<? extends RemoteException>) Class.forName(exceptionType.getTypeName());
                Constructor<? extends RemoteException> exceptionConstructor =
                        exceptionClass.getConstructor(error.getClass(), int.class);
                throw exceptionConstructor.newInstance(error, code);
            } catch (RemoteException remoteException) {
                // rethrow the created remote exception
                throw remoteException;
            } catch (Exception e) {
                UnknownRemoteException unknownRemoteException =
                        new UnknownRemoteException(code, new String(body, StandardCharsets.UTF_8));
                unknownRemoteException.initCause(e);
                throw unknownRemoteException;
            }
        }

        throw new UnknownRemoteException(code, new String(body, StandardCharsets.UTF_8));
    }

    private record DeserializerExceptionPair<U extends AbstractSerializableError<?>>(
            Encoding.Deserializer<U> deserializer, TypeMarker<? extends RemoteException> exceptionType) {}

    // Purely to provide Java type inference information.
    private static <U extends AbstractSerializableError<?>> DeserializerExceptionPair<U> createDeserializerForException(
            Encoding encoding, ErrorExceptionPair<U> pair) {
        return new DeserializerExceptionPair<>(encoding.deserializer(pair.errorType()), pair.exceptionType());
    }
}
