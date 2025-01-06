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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
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
import com.palantir.conjure.java.serialization.ObjectMappers;
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
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();
    private final Map<String, Encoding.Deserializer<? extends T>> errorNameToJsonDeserializerMap;

    EndpointErrorDecoder(
            Map<String, TypeMarker<? extends T>> errorNameToTypeMap, Optional<Encoding> maybeJsonEncoding) {
        this.errorNameToJsonDeserializerMap = maybeJsonEncoding
                .<Map<String, Encoding.Deserializer<? extends T>>>map(
                        jsonEncoding -> errorNameToTypeMap.entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey, entry -> jsonEncoding.deserializer(entry.getValue()))))
                .orElseGet(Collections::emptyMap);
    }

    public boolean isError(Response response) {
        return 300 <= response.code() && response.code() <= 599;
    }

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

    private T decodeInternal(Response response) {
        Optional<RuntimeException> maybeQosException = checkCode(response);
        if (maybeQosException.isPresent()) {
            throw maybeQosException.get();
        }
        int code = response.code();
        String body;
        try {
            body = toString(response.body());
        } catch (NullPointerException | IOException e) {
            UnknownRemoteException exception = new UnknownRemoteException(code, "<unparseable>");
            exception.initCause(e);
            throw exception;
        }

        Optional<String> contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        String jsonContentType = "application/json";
        if (contentType.isPresent() && Encodings.matchesContentType(jsonContentType, contentType.get())) {
            try {
                JsonNode node = MAPPER.readTree(body);
                JsonNode errorNameNode = node.get("errorName");
                if (errorNameNode == null) {
                    throwRemoteException(body, code);
                }
                Optional<Deserializer<? extends T>> maybeDeserializer =
                        Optional.ofNullable(errorNameToJsonDeserializerMap.get(errorNameNode.asText()));
                if (maybeDeserializer.isEmpty()) {
                    throwRemoteException(body, code);
                }
                return maybeDeserializer
                        .get()
                        .deserialize(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            } catch (RemoteException remoteException) {
                // rethrow the created remote exception
                throw remoteException;
            } catch (Exception e) {
                throw new UnknownRemoteException(code, body);
            }
        }

        throw new UnknownRemoteException(code, body);
    }

    private static void throwRemoteException(String body, int code) throws IOException {
        SerializableError serializableError = MAPPER.readValue(body, SerializableError.class);
        throw new RemoteException(serializableError, code);
    }

    static String toString(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
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
}
