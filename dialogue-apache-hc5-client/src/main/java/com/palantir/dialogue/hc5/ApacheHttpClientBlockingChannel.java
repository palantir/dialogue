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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.net.HttpHeaders;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.dialogue.core.BaseUrl;
import com.palantir.dialogue.core.SafeToRetryForIdempotentEndpoints;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.logsafe.exceptions.SafeExceptions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.api.TraceHttpHeaders;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApacheHttpClientBlockingChannel implements BlockingChannel {
    private static final Logger log = LoggerFactory.getLogger(ApacheHttpClientBlockingChannel.class);

    private final ApacheHttpClientChannels.CloseableClient client;
    private final BaseUrl baseUrl;
    private final ResponseLeakDetector responseLeakDetector;

    ApacheHttpClientBlockingChannel(
            ApacheHttpClientChannels.CloseableClient client, URL baseUrl, ResponseLeakDetector responseLeakDetector) {
        this.client = client;
        this.baseUrl = BaseUrl.of(baseUrl);
        this.responseLeakDetector = responseLeakDetector;
    }

    @Override
    public Response execute(Endpoint endpoint, Request request) throws IOException {
        // Create base request given the URL
        URL target = baseUrl.render(endpoint, request);
        ClassicRequestBuilder builder =
                ClassicRequestBuilder.create(endpoint.httpMethod().name()).setUri(target.toString());

        // Fill headers
        request.headerParams().forEach(builder::addHeader);

        if (request.body().isPresent()) {
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.GET, "GET endpoints must not have a request body");
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.HEAD, "HEAD endpoints must not have a request body");
            Preconditions.checkArgument(
                    endpoint.httpMethod() != HttpMethod.OPTIONS, "OPTIONS endpoints must not have a request body");
            RequestBody body = request.body().get();
            setBody(builder, body);
        } else if (requiresEmptyBody(endpoint)) {
            builder.setEntity(EmptyHttpEntity.INSTANCE);
        }
        long startTime = System.nanoTime();
        try {
            CloseableHttpResponse httpClientResponse = client.apacheClient().execute(builder.build());
            // Defensively ensure that resources are closed if failures occur within this block,
            // for example HttpClientResponse allocation may throw an OutOfMemoryError.
            boolean close = true;
            try {
                Response dialogueResponse = new HttpClientResponse(client, httpClientResponse);
                Response leakDetectingResponse = responseLeakDetector.wrap(dialogueResponse, endpoint);
                close = false;
                return leakDetectingResponse;
            } finally {
                if (close) {
                    httpClientResponse.close();
                }
            }
        } catch (ConnectTimeoutException e) {
            // ConnectTimeoutException must be wrapped so it may be retried. SocketTimeoutExceptions are
            // not retried by default, so ours implements SafeLoggable and retains the simple-name for
            // cleaner metrics.
            throw new SafeConnectTimeoutException(e, failureDiagnosticArgs(endpoint, request, startTime));
        } catch (NoHttpResponseException e) {
            // This should not be retried on non-idempotent endpoints since it can happen when the server
            // already processed the request, however this can also happen if a persistent connection is timed out and
            // closed by the server or intermediate proxy, in that case it's important that we do retry. Therefore, we
            // only allow retries if less than 5 seconds have passed.
            if (Duration.ofNanos(System.nanoTime() - startTime).getSeconds() <= 5) {
                throw e;
            }
            throw new SafeToRetryForIdempotentEndpoints(
                    "No http response exception occurred after more than 5 seconds have passed", e);

        } catch (Throwable t) {
            // We can't wrap all potential exception types, that would cause the failure to lose some amount of type
            // information. Instead, we add a suppressed throwable with no stack trace which acts as a courier
            // for our diagnostic information, ensuring it can be recorded in the logs.
            t.addSuppressed(new Diagnostic(failureDiagnosticArgs(endpoint, request, startTime)));
            throw t;
        }
    }

    private Arg<?>[] failureDiagnosticArgs(Endpoint endpoint, Request request, long startTimeNanos) {
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
        return new Arg<?>[] {
            SafeArg.of("durationMillis", durationMillis),
            SafeArg.of("connectTimeout", client.clientConfiguration().connectTimeout()),
            SafeArg.of("socketTimeout", client.clientConfiguration().readTimeout()),
            SafeArg.of("clientName", client.name()),
            SafeArg.of("serviceName", endpoint.serviceName()),
            SafeArg.of("endpointName", endpoint.endpointName()),
            SafeArg.of("requestTraceId", request.headerParams().get(TraceHttpHeaders.TRACE_ID)),
            // Request span ID can be used to associate a failed request with a request log line on the server.
            SafeArg.of("requestSpanId", request.headerParams().get(TraceHttpHeaders.SPAN_ID)),
        };
    }

    private static final class Diagnostic extends RuntimeException implements SafeLoggable {

        private static final String SAFE_MESSAGE = "Client Failure Diagnostic Information";

        private final List<Arg<?>> args;

        Diagnostic(Arg<?>[] args) {
            super(SafeExceptions.renderMessage(SAFE_MESSAGE, args));
            this.args = Collections.unmodifiableList(Arrays.asList(args));
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

    // https://tools.ietf.org/html/rfc7230#section-3.3.2 recommends setting a content-length
    // on empty post requests. Some components may respond 411 if the content-length is not present.
    private static boolean requiresEmptyBody(Endpoint endpoint) {
        HttpMethod method = endpoint.httpMethod();
        return method == HttpMethod.POST || method == HttpMethod.PUT;
    }

    private static void setBody(ClassicRequestBuilder builder, RequestBody body) {
        builder.setEntity(new RequestBodyEntity(body, contentLength(body, builder)));
    }

    private static OptionalLong contentLength(RequestBody requestBody, ClassicRequestBuilder builder) {
        Header contentLengthHeader = builder.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        OptionalLong headerContentLength = OptionalLong.empty();
        if (contentLengthHeader != null) {
            builder.removeHeaders(HttpHeaders.CONTENT_LENGTH);
            String contentLengthValue = contentLengthHeader.getValue();
            try {
                headerContentLength = OptionalLong.of(Long.parseLong(contentLengthValue));
            } catch (NumberFormatException nfe) {
                log.warn(
                        "Failed to parse content-length value '{}'",
                        SafeArg.of(HttpHeaders.CONTENT_LENGTH, contentLengthValue),
                        nfe);
            }
        }

        if (headerContentLength.isPresent() && requestBody.contentLength().isPresent()) {
            long headerContentLengthValue = headerContentLength.getAsLong();
            long requestBodyContentLength = requestBody.contentLength().getAsLong();
            if (headerContentLengthValue != requestBodyContentLength) {
                log.warn(
                        "Content lengths do not match",
                        SafeArg.of(HttpHeaders.CONTENT_LENGTH, headerContentLengthValue),
                        SafeArg.of("requestBodyContentLength", requestBodyContentLength));
            }
        }

        if (headerContentLength.isPresent()) {
            return headerContentLength;
        }

        return requestBody.contentLength();
    }

    private static final class HttpClientResponse implements Response {

        private final CloseableHttpResponse response;
        // Client reference is used to prevent premature termination
        @Nullable
        private ApacheHttpClientChannels.CloseableClient client;

        @Nullable
        private ListMultimap<String, String> headers;

        HttpClientResponse(ApacheHttpClientChannels.CloseableClient client, CloseableHttpResponse response) {
            this.client = client;
            this.response = response;
        }

        @Override
        public InputStream body() {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    return new ResponseInputStream(client, entity.getContent());
                } catch (IOException e) {
                    throw new SafeRuntimeException("Failed to get response stream", e);
                }
            }
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int code() {
            return response.getCode();
        }

        @Override
        public ListMultimap<String, String> headers() {
            if (headers == null) {
                ListMultimap<String, String> tmpHeaders = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                        .arrayListValues()
                        .build();
                Iterator<Header> headerIterator = response.headerIterator();
                while (headerIterator.hasNext()) {
                    Header header = headerIterator.next();
                    String value = header.getValue();
                    if (value != null) {
                        tmpHeaders.put(header.getName(), value);
                    }
                }
                headers = Multimaps.unmodifiableListMultimap(tmpHeaders);
            }
            return headers;
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            return Optional.ofNullable(response.getFirstHeader(header)).map(Header::getValue);
        }

        @Override
        public void close() {
            client = null;
            try {
                response.close();
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to close response", e);
            }
        }

        @Override
        public String toString() {
            return "HttpClientResponse{response=" + response + ", client=" + client + '}';
        }
    }

    private static final class RequestBodyEntity implements HttpEntity {

        private final RequestBody requestBody;
        private final Header contentType;
        private final OptionalLong contentLength;

        RequestBodyEntity(RequestBody requestBody, OptionalLong contentLength) {
            this.requestBody = requestBody;
            this.contentType = new BasicHeader(HttpHeaders.CONTENT_TYPE, requestBody.contentType());
            this.contentLength = contentLength;
        }

        @Override
        public boolean isRepeatable() {
            // n.b. Proxy authentication can only be negotiated on repeatable requests.
            // Subsequent requests needn't be repeatable as state is cached by the client.
            return requestBody.repeatable();
        }

        @Override
        public boolean isChunked() {
            return !contentLength.isPresent();
        }

        @Override
        public Set<String> getTrailerNames() {
            return Collections.emptySet();
        }

        @Override
        public long getContentLength() {
            return contentLength.orElse(-1);
        }

        @Override
        public String getContentType() {
            return contentType.getValue();
        }

        @Override
        @Nullable
        public String getContentEncoding() {
            return null;
        }

        @Override
        public InputStream getContent() throws UnsupportedOperationException {
            throw new UnsupportedOperationException("getContent is not supported, writeTo should be used");
        }

        @Override
        public void writeTo(OutputStream outStream) throws IOException {
            requestBody.writeTo(outStream);
        }

        @Override
        public boolean isStreaming() {
            // Applies to responses.
            return false;
        }

        @Override
        @Nullable
        public Supplier<List<? extends Header>> getTrailers() {
            return null;
        }

        @Override
        public String toString() {
            return "RequestBodyEntity{requestBody=" + requestBody + '}';
        }

        @Override
        public void close() {}
    }

    private static final class ResponseInputStream extends FilterInputStream {

        // Client reference is used to prevent premature termination
        @Nullable
        private ApacheHttpClientChannels.CloseableClient client;

        ResponseInputStream(@Nullable ApacheHttpClientChannels.CloseableClient client, InputStream stream) {
            super(stream);
            this.client = client;
        }

        @Override
        public void close() throws IOException {
            client = null;
            super.close();
        }

        @Override
        public String toString() {
            return "ResponseInputStream{client=" + client + ", in=" + in + '}';
        }
    }
}
