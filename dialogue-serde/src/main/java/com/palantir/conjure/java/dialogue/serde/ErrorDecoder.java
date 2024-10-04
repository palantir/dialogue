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
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeExceptions;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Extracts and returns a {@link RemoteException} from an {@link Response}.
 * The extracted {@link RemoteException} is returned rather than thrown. Decoders may throw exceptions (other than
 * {@link RemoteException}) if a {@link RemoteException} could not be extracted, e.g., when the given {@link
 * Response} does not adhere to an expected format.
 */
public enum ErrorDecoder {
    INSTANCE;

    private static final SafeLogger log = SafeLoggerFactory.get(ErrorDecoder.class);
    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

    public boolean isError(Response response) {
        return 300 <= response.code() && response.code() <= 599;
    }

    public RuntimeException decode(Response response) {
        if (log.isDebugEnabled()) {
            log.debug("Received an error response", diagnosticArgs(response));
        }
        RuntimeException result = decodeInternal(response);
        result.addSuppressed(diagnostic(response));
        return result;
    }

    private RuntimeException decodeInternal(Response response) {
        // TODO(rfink): What about HTTP/101 switching protocols?
        // TODO(rfink): What about HEAD requests?

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
                        return remoteException;
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
                return response.getFirstHeader(HttpHeaders.RETRY_AFTER)
                        .map(Longs::tryParse)
                        .map(Duration::ofSeconds)
                        .map(duration -> QosException.throttle(qosReason(response), duration))
                        .orElseGet(() -> QosException.throttle(qosReason(response)));
            case 503:
                return QosException.unavailable(qosReason(response));
        }

        String body;
        try {
            body = toString(response.body());
        } catch (NullPointerException | IOException e) {
            UnknownRemoteException exception = new UnknownRemoteException(code, "<unparseable>");
            exception.initCause(e);
            return exception;
        }

        Optional<String> contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType.isPresent() && Encodings.matchesContentType("application/json", contentType.get())) {
            try {
                SerializableError serializableError = MAPPER.readValue(body, SerializableError.class);
                return new RemoteException(serializableError, code);
            } catch (Exception e) {
                return new UnknownRemoteException(code, body);
            }
        }

        return new UnknownRemoteException(code, body);
    }

    private static String toString(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
        }
    }

    private static ResponseDiagnostic diagnostic(Response response) {
        return new ResponseDiagnostic(diagnosticArgs(response));
    }

    private static ImmutableList<Arg<?>> diagnosticArgs(Response response) {
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
