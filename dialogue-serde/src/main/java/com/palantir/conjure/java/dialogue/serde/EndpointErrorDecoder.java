/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.google.common.primitives.Longs;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.QosReasons;
import com.palantir.conjure.java.api.errors.QosReasons.QosResponseDecodingAdapter;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.dialogue.serde.Encoding.Deserializer;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeExceptions;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Extracts the error from a {@link Response}.
 * <p>If the error's name is in the {@link #errorNameToJsonDeserializerMap}, this class attempts to deserialize the
 * {@link Response} body as JSON, to the error type. Otherwise, a {@link RemoteException} is thrown. If the
 * {@link Response} does not adhere to the expected format, an {@link UnknownRemoteException} is thrown.
 *
 * @param <T> the base type of the endpoint response. It's a union of the result type and all the error types.
 */
final class EndpointErrorDecoder<T> {
    private static final SafeLogger log = SafeLoggerFactory.get(EndpointErrorDecoder.class);
    // Errors are currently expected to be JSON objects. See
    // https://palantir.github.io/conjure/#/docs/spec/wire?id=_55-conjure-errors. As there is greater adoption of
    // endpoint associated errors and larger parameters, we may be interested in supporting SMILE/CBOR for more
    // performant handling of larger paramater payloads.
    private static final Encoding JSON_ENCODING = Encodings.json();
    private static final Deserializer<NamedError> NAMED_ERROR_DESERIALIZER =
            JSON_ENCODING.deserializer(new TypeMarker<>() {});
    private static final Deserializer<SerializableError> SERIALIZABLE_ERROR_DESERIALIZER =
            JSON_ENCODING.deserializer(new TypeMarker<>() {});
    private final Map<String, Encoding.Deserializer<? extends T>> errorNameToJsonDeserializerMap;

    EndpointErrorDecoder(Map<String, TypeMarker<? extends T>> errorNameToTypeMap) {
        this(errorNameToTypeMap, Optional.empty());
    }

    EndpointErrorDecoder(
            Map<String, TypeMarker<? extends T>> errorNameToTypeMap, Optional<Encoding> maybeJsonEncoding) {
        this.errorNameToJsonDeserializerMap = ImmutableMap.copyOf(
                Maps.transformValues(errorNameToTypeMap, maybeJsonEncoding.orElse(JSON_ENCODING)::deserializer));
    }

    boolean isError(Response response) {
        return 300 <= response.code() && response.code() <= 599;
    }

    T decode(Response response) {
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

    @SuppressWarnings("for-rollout:StatementSwitchToExpressionSwitch")
    Optional<RuntimeException> checkCode(Response response) {
        int code = response.code();
        switch (code) {
            case 308:
                Optional<String> location = response.getFirstHeader(HttpHeaders.LOCATION);
                if (location.isPresent()) {
                    String locationHeader = location.get();
                    try {
                        UnknownRemoteException remoteException = new UnknownRemoteException(code, "");
                        remoteException.initCause(
                                QosException.retryOther(qosReason(response), new URL(locationHeader)));
                        return Optional.of(remoteException);
                    } catch (MalformedURLException e) {
                        log.error(
                                "Failed to parse location header for QosException.RetryOther",
                                UnsafeArg.of("locationHeader", locationHeader),
                                e);
                    }
                } else {
                    log.error("Retrieved HTTP status code 308 without Location header, cannot perform "
                            + "redirect. This appears to be a server-side protocol violation.");
                }
                break;
            case 429:
                return Optional.of(response.getFirstHeader(HttpHeaders.RETRY_AFTER)
                        .map(Longs::tryParse)
                        .map(Duration::ofSeconds)
                        .map(duration -> QosException.throttle(qosReason(response), duration))
                        .orElseGet(() -> QosException.throttle(qosReason(response))));
            case 503:
                return Optional.of(QosException.unavailable(qosReason(response)));
        }
        return Optional.empty();
    }

    private @Nullable String extractErrorName(byte[] body) {
        try {
            NamedError namedError = NAMED_ERROR_DESERIALIZER.deserialize(new ByteArrayInputStream(body));
            if (namedError == null) {
                return null;
            }
            return namedError.errorName();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private T decodeInternal(Response response) {
        Optional<RuntimeException> maybeQosException = checkCode(response);
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
                Deserializer<? extends T> deserializer = errorNameToJsonDeserializerMap.get(errorName);
                if (deserializer == null) {
                    throw createRemoteException(body, code);
                }
                return deserializer.deserialize(new ByteArrayInputStream(body));
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

    private static RemoteException createRemoteException(byte[] body, int code) throws IOException {
        SerializableError serializableError =
                SERIALIZABLE_ERROR_DESERIALIZER.deserialize(new ByteArrayInputStream(body));
        return new RemoteException(serializableError, code);
    }

    private static byte[] toByteArray(InputStream body) throws IOException {
        try (body) {
            return body.readAllBytes();
        }
    }

    static ResponseDiagnostic diagnostic(Response response) {
        return new ResponseDiagnostic(diagnosticArgs(response));
    }

    static ImmutableList<Arg<?>> diagnosticArgs(Response response) {
        ImmutableList.Builder<Arg<?>> args = ImmutableList.<Arg<?>>builder().add(SafeArg.of("status", response.code()));
        recordHeader(HttpHeaders.SERVER, response, args);
        recordHeader(HttpHeaders.CONTENT_TYPE, response, args);
        recordHeader(HttpHeaders.CONTENT_LENGTH, response, args);
        recordHeader(HttpHeaders.CONNECTION, response, args);
        recordHeader(HttpHeaders.DATE, response, args);
        recordHeader("x-envoy-response-flags", response, args);
        recordHeader("x-envoy-response-code-details", response, args);
        recordHeader("Response-Flags", response, args);
        recordHeader("Response-Code-Details", response, args);
        return args.build();
    }

    private static void recordHeader(String header, Response response, ImmutableList.Builder<Arg<?>> args) {
        response.getFirstHeader(header).ifPresent(server -> args.add(SafeArg.of(header, server)));
    }

    private static final class ResponseDiagnostic extends RuntimeException implements SafeLoggable {

        private static final String SAFE_MESSAGE = "Response Diagnostic Information";

        private final ImmutableList<Arg<?>> args;

        ResponseDiagnostic(ImmutableList<Arg<?>> args) {
            super(SafeExceptions.renderMessage(SAFE_MESSAGE, args.toArray(new Arg<?>[0])));
            this.args = args;
        }

        @Override
        public String getLogMessage() {
            return SAFE_MESSAGE;
        }

        @Override
        public List<Arg<?>> getArgs() {
            return args;
        }

        @Override
        @SuppressWarnings("UnsynchronizedOverridesSynchronized") // nop
        public Throwable fillInStackTrace() {
            // no-op: stack trace generation is expensive, this type exists
            // to simply associate diagnostic information with a failure.
            return this;
        }
    }

    private static QosReason qosReason(Response response) {
        return QosReasons.parseFromResponse(response, DialogueQosResponseDecodingAdapter.INSTANCE);
    }

    private enum DialogueQosResponseDecodingAdapter implements QosResponseDecodingAdapter<Response> {
        INSTANCE;

        @Override
        public Optional<String> getFirstHeader(Response response, String headerName) {
            return response.getFirstHeader(headerName);
        }
    }

    record NamedError(@JsonProperty("errorName") String errorName) {}
}
